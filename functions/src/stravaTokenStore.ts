import { createHash } from "node:crypto";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export interface StravaTokens {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  athleteId: string;
  athleteName: string;
  scopes: string[];
}

const privateConnectionRef = (uid: string) =>
  getFirestore().doc(`users/${uid}/private/stravaConnection`);

export async function saveConnection(uid: string, tokens: StravaTokens): Promise<void> {
  await privateConnectionRef(uid).set({
    ...tokens,
    connectedAt: Timestamp.now(),
    refreshLeaseUntil: null,
  });
}

export async function connectionSummary(uid: string): Promise<{ connected: boolean; athleteName?: string }> {
  const snapshot = await privateConnectionRef(uid).get();
  if (!snapshot.exists) return { connected: false };
  const data = snapshot.data();
  return { connected: true, athleteName: typeof data?.athleteName === "string" ? data.athleteName : undefined };
}

export async function getRefreshToken(uid: string): Promise<string | null> {
  const snapshot = await privateConnectionRef(uid).get();
  const token = snapshot.data()?.refreshToken;
  return typeof token === "string" && token.length > 0 ? token : null;
}

export async function getUsableAccessToken(
  uid: string,
  clientId: string,
  clientSecret: string,
): Promise<string> {
  const ref = privateConnectionRef(uid);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw new HttpsError("failed-precondition", "Strava is not connected.");
  const current = snapshot.data()!;
  const now = Math.floor(Date.now() / 1000);
  if (typeof current.accessToken === "string" && Number(current.expiresAt) > now + 300) {
    return current.accessToken;
  }
  if (typeof current.refreshToken !== "string" || !current.refreshToken) {
    throw new HttpsError("failed-precondition", "Strava authorization must be renewed.");
  }

  const leaseId = createHash("sha256").update(`${uid}:${Date.now()}:${Math.random()}`).digest("hex");
  const claim = await getFirestore().runTransaction(async (transaction) => {
    const latest = await transaction.get(ref);
    if (!latest.exists) throw new HttpsError("failed-precondition", "Strava is not connected.");
    const latestData = latest.data()!;
    if (typeof latestData.accessToken === "string" && Number(latestData.expiresAt) > now + 300) {
      return { accessToken: latestData.accessToken, refreshToken: null as string | null };
    }
    const leaseUntil = latestData.refreshLeaseUntil as Timestamp | null | undefined;
    if (leaseUntil && leaseUntil.toMillis() > Date.now()) return null;
    if (typeof latestData.refreshToken !== "string" || !latestData.refreshToken) {
      throw new HttpsError("failed-precondition", "Strava authorization must be renewed.");
    }
    transaction.update(ref, {
      refreshLeaseId: leaseId,
      refreshLeaseUntil: Timestamp.fromMillis(Date.now() + 30_000),
    });
    return { accessToken: null as string | null, refreshToken: latestData.refreshToken };
  });
  if (claim?.accessToken) return claim.accessToken;
  if (!claim?.refreshToken) throw new HttpsError("unavailable", "Strava token refresh is already in progress. Retry shortly.");

  try {
    const form = new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: "refresh_token",
      refresh_token: claim.refreshToken,
    });
    const response = await fetch("https://www.strava.com/oauth/token", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form,
    });
    if (!response.ok) {
      if (response.status === 401 || response.status === 400) {
        throw new HttpsError("failed-precondition", "Strava authorization must be renewed.");
      }
      throw new HttpsError("unavailable", "Unable to refresh Strava authorization.");
    }
    const refreshed = await response.json() as { access_token?: string; refresh_token?: string; expires_at?: number };
    if (!refreshed.access_token || !refreshed.refresh_token || !refreshed.expires_at) {
      throw new HttpsError("unavailable", "Strava returned an incomplete token response.");
    }
    await getFirestore().runTransaction(async (transaction) => {
      const latest = await transaction.get(ref);
      if (!latest.exists || latest.data()?.refreshLeaseId !== leaseId) {
        throw new HttpsError("aborted", "Strava token refresh lease expired.");
      }
      transaction.update(ref, {
        accessToken: refreshed.access_token,
        refreshToken: refreshed.refresh_token,
        expiresAt: refreshed.expires_at,
        refreshLeaseId: null,
        refreshLeaseUntil: null,
      });
    });
    return refreshed.access_token;
  } catch (error) {
    await getFirestore().runTransaction(async (transaction) => {
      const latest = await transaction.get(ref);
      if (latest.exists && latest.data()?.refreshLeaseId === leaseId) {
        transaction.update(ref, { refreshLeaseId: null, refreshLeaseUntil: null });
      }
    }).catch(() => undefined);
    throw error;
  }
}

export async function deleteConnection(uid: string): Promise<void> {
  await privateConnectionRef(uid).delete();
}
