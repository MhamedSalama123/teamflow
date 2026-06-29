/**
 * Browser-side Google OAuth2 (authorization-code) configuration.
 *
 * Replace GOOGLE_CLIENT_ID with the OAuth client ID from the Google Cloud console. The
 * redirect URI is derived from the current origin and must be registered in Google Cloud as
 * well as matching the backend's GOOGLE_REDIRECT_URI.
 */
export const GOOGLE_CLIENT_ID =
  '1025057911520-0ede0s968df42p8h5fkr8fajod9gju5k.apps.googleusercontent.com';
const GOOGLE_AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';

export function googleRedirectUri(): string {
  return `${window.location.origin}/auth/google/callback`;
}

export function buildGoogleAuthUrl(): string {
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: googleRedirectUri(),
    response_type: 'code',
    scope: 'openid email profile',
    prompt: 'select_account',
  });
  return `${GOOGLE_AUTH_ENDPOINT}?${params.toString()}`;
}
