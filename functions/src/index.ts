import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  cancelAuthorization,
  completeAuthorization,
  createAuthorizationUrl,
  revokeConnection,
} from "./stravaOAuth";
import { deleteConnection, getRefreshToken, getUsableAccessToken, connectionSummary } from "./stravaTokenStore";

initializeApp();
getFirestore().settings({ ignoreUndefinedProperties: true });

const stravaClientId = defineString("STRAVA_CLIENT_ID");
const stravaRedirectUri = defineString("STRAVA_REDIRECT_URI", {
  default: "https://helpdev.com.br/apps/velocimetro-alerta/strava/callback",
});
const stravaClientSecret = defineSecret("STRAVA_CLIENT_SECRET");
const callableOptions = { enforceAppCheck: true, region: "southamerica-east1" } as const;

function requireUid(uid: string | undefined): string {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in with Google before connecting Strava.");
  return uid;
}

function requireMap(data: unknown): Record<string, unknown> {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new HttpsError("invalid-argument", "Request data is invalid.");
  }
  return data as Record<string, unknown>;
}

export const beginStravaConnect = onCall(callableOptions, async (request) => {
  const uid = requireUid(request.auth?.uid);
  const authorizationUrl = await createAuthorizationUrl(uid, stravaClientId.value(), stravaRedirectUri.value());
  return { authorizationUrl };
});

export const completeStravaConnect = onCall({ ...callableOptions, secrets: [stravaClientSecret] }, async (request) => {
  const uid = requireUid(request.auth?.uid);
  const data = requireMap(request.data);
  await completeAuthorization(
    uid,
    typeof data.state === "string" ? data.state : "",
    typeof data.code === "string" ? data.code : "",
    stravaClientId.value(),
    stravaClientSecret.value(),
  );
  return { connected: true };
});

export const cancelStravaConnect = onCall(callableOptions, async (request) => {
  const uid = requireUid(request.auth?.uid);
  const data = requireMap(request.data);
  await cancelAuthorization(uid, typeof data.state === "string" ? data.state : "");
  return { cancelled: true };
});

export const getStravaConnectionState = onCall(callableOptions, async (request) => {
  const uid = requireUid(request.auth?.uid);
  return connectionSummary(uid);
});

export const getStravaAccessToken = onCall({ ...callableOptions, secrets: [stravaClientSecret] }, async (request) => {
  const uid = requireUid(request.auth?.uid);
  const accessToken = await getUsableAccessToken(uid, stravaClientId.value(), stravaClientSecret.value());
  return { accessToken };
});

export const disconnectStrava = onCall({ ...callableOptions, secrets: [stravaClientSecret] }, async (request) => {
  const uid = requireUid(request.auth?.uid);
  const refreshToken = await getRefreshToken(uid);
  if (refreshToken) await revokeConnection(refreshToken, stravaClientId.value(), stravaClientSecret.value());
  await deleteConnection(uid);
  return { connected: false };
});
