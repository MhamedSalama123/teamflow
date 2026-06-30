package com.teamflow.backend.user.dto;

import com.teamflow.backend.user.User;

/** A single hit in the user directory search. Exposes only public profile fields. */
public record UserSearchResult(
        Long id,
        String username,
        String fullName,
        String photoUrl,
        String jobTitle,
        String location) {

    public static UserSearchResult from(User user) {
        return new UserSearchResult(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPhotoUrl(),
                user.getJobTitle(),
                user.getLocation());
    }
}
