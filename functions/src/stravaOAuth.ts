import { createHash, randomBytes } from "node:crypto";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { StravaTokens, saveConnection } from "./stravaTokenStore";

const REQUIRED_SCOPE = "activity:write";
const STATE_TTL_MILLIS = 10 * 60 * 1000;

function stateDocumentId(state: string): string {
  return createHash("sha256").update(state).digest("hex");
}

export async function createAuthorizationUrl(uid: string, clientId: string, redirectUri: string): Promise<string> {
  if (!clientId || !redirectUri.startsWith("https://")) {
    throw new HttpsError("failed-precondition", "Strava OAuth is not configured.");
  }
  const state = randomBytes(32).toString("base64url");
  await getFirestore().doc(`stravaOAuthStates/${stateDocumentId(state)}`).create({
    uid,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + STATE_TTL_MILLIS),
  });
  const url = new URL("https://www.strava.com/oauth/mobile/authorize");
  url.search = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    approval_prompt: "auto",
    scope: REQUIRED_SCOPE,
    state,
  }).toString();
  return url.toString();
}

export async function cancelAuthorization(uid: string, state: string): Promise<void> {
  if (!state || state.length > 200) return;
  const ref = getFirestore().doc(`stravaOAuthStates/${stateDocumentId(state)}`);
  await getFirestore().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.exists && snapshot.data()?.uid === uid) transaction.delete(ref);
  });
}

export async function completeAuthorization(
  uid: string,
  state: string,
  code: string,
  clientId: string,
  clientSecret: string,
): Promise<void> {
  if (!state || state.length > 200 || !code || code.length > 500) {
    throw new HttpsError("invalid-argument", "Invalid Strava authorization response.");
  }
  const stateRef = getFirestore().doc(`stravaOAuthStates/${stateDocumentId(state)}`);
  const validState = await getFirestore().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(stateRef);
    const data = snapshot.data();
    if (!snapshot.exists || data?.uid !== uid || !(data.expiresAt instanceof Timestamp) || data.expiresAt.toMillis() < Date.now()) {
      return false;
    }
    transaction.delete(stateRef);
    return true;
  });
  if (!validState) throw new HttpsError("permission-denied", "Strava authorization expired or was already used.");

  const response = await fetch("https://www.strava.com/oauth/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      code,
      grant_type: "authorization_code",
    }),
  });
  if (!response.ok) throw new HttpsError("unavailable", "Strava could not complete authorization.");
  const payload = await response.json() as {
    access_token?: string;
    refresh_token?: string;
    expires_at?: number;
    scope?: string;
    athlete?: { id?: number | string; firstname?: string; lastname?: string; username?: string };
  };
  const scopes = (payload.scope ?? "").split(/[ ,]+/).filter(Boolean);
  if (!scopes.includes(REQUIRED_SCOPE)) {
    throw new HttpsError("permission-denied", "Strava upload permission was not granted.");
  }
  if (!payload.access_token || !payload.refresh_token || !payload.expires_at || payload.athlete?.id == null) {
    throw new HttpsError("unavailable", "Strava returned an incomplete authorization response.");
  }
  const athleteName = [payload.athlete.firstname, payload.athlete.lastname].filter(Boolean).join(" ").trim()
    || payload.athlete.username
    || "Strava";
  const tokens: StravaTokens = {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token,
    expiresAt: payload.expires_at,
    athleteId: String(payload.athlete.id),
    athleteName,
    scopes,
  };
  await saveConnection(uid, tokens);
}

export async function revokeConnection(refreshToken: string, clientId: string, clientSecret: string): Promise<void> {
  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const response = await fetch("https://www.strava.com/oauth/revoke", {
    method: "POST",
    headers: {
      authorization: `Basic ${credentials}`,
      "content-type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({ token: refreshToken, token_type_hint: "refresh_token" }),
  });
  if (!response.ok) throw new HttpsError("unavailable", "Strava authorization could not be revoked.");
}
