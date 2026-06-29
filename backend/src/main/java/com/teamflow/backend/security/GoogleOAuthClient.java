package com.teamflow.backend.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.teamflow.backend.auth.GoogleAuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Performs the server-side half of the Google authorization-code flow: it swaps the code the
 * browser obtained from Google for an access token, then loads the user's profile. Endpoint
 * URLs and credentials come from the "google" {@link ClientRegistration} configured in
 * application.properties, so no provider URLs are hard-coded here.
 */
@Component
public class GoogleOAuthClient {

    private static final String REGISTRATION_ID = "google";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    public GoogleUserInfo exchangeCodeForUser(String code) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        if (google == null) {
            throw new GoogleAuthenticationException("Google OAuth2 client is not configured");
        }
        String accessToken = requestAccessToken(google, code);
        return requestUserInfo(google, accessToken);
    }

    private String requestAccessToken(ClientRegistration google, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", google.getRedirectUri());
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());

        try {
            TokenResponse response = restClient.post()
                    .uri(google.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new GoogleAuthenticationException("Google did not return an access token");
            }
            return response.accessToken();
        } catch (RestClientException e) {
            throw new GoogleAuthenticationException("Failed to exchange authorization code with Google", e);
        }
    }

    private GoogleUserInfo requestUserInfo(ClientRegistration google, String accessToken) {
        try {
            GoogleUserInfo userInfo = restClient.get()
                    .uri(google.getProviderDetails().getUserInfoEndpoint().getUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfo.class);
            if (userInfo == null || userInfo.email() == null) {
                throw new GoogleAuthenticationException("Google did not return an email address");
            }
            return userInfo;
        } catch (RestClientException e) {
            throw new GoogleAuthenticationException("Failed to load the Google user profile", e);
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
