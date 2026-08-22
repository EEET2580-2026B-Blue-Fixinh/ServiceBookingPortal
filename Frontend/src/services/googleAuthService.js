import { GOOGLE_CLIENT_ID, GOOGLE_REDIRECT_URI } from "../config/env";

const OAUTH_STATE_KEY = "fixinh.google.oauth.state";

export function googleRedirectUri() {
  return GOOGLE_REDIRECT_URI || `${window.location.origin}/login`;
}

export function createGoogleAuthorizationUrl() {
  const state = crypto.randomUUID();
  sessionStorage.setItem(OAUTH_STATE_KEY, state);
  const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  url.searchParams.set("client_id", GOOGLE_CLIENT_ID);
  url.searchParams.set("redirect_uri", googleRedirectUri());
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid email profile");
  url.searchParams.set("state", state);
  url.searchParams.set("prompt", "select_account");
  return url.toString();
}

export function consumeGoogleOAuthState(returnedState) {
  const expected = sessionStorage.getItem(OAUTH_STATE_KEY);
  sessionStorage.removeItem(OAUTH_STATE_KEY);
  return Boolean(expected && returnedState && expected === returnedState);
}
