package com.teamflow.backend.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the Google userinfo response we care about. Unknown fields (sub, picture,
 * locale, ...) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfo(String email, String name) {
}
