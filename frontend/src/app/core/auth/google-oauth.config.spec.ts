import { buildGoogleAuthUrl, googleRedirectUri } from './google-oauth.config';

describe('google-oauth config', () => {
  it('derives the redirect URI from the current origin', () => {
    expect(googleRedirectUri()).toBe(`${window.location.origin}/auth/google/callback`);
  });

  it('builds an authorization URL with the required parameters', () => {
    const url = buildGoogleAuthUrl();

    expect(url).toContain('https://accounts.google.com/o/oauth2/v2/auth');
    expect(url).toContain('response_type=code');
    expect(url).toContain('scope=openid+email+profile');
    expect(url).toContain('prompt=select_account');
    expect(url).toContain('redirect_uri=');
    expect(url).toContain(encodeURIComponent('/auth/google/callback'));
  });
});
