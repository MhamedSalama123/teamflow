package com.teamflow.backend.user.dto;

import com.teamflow.backend.user.User;

public record UserProfileResponse(
        Long id,
        String email,
        String username,
        String bio,
        String jobTitle,
        String location,
        String phoneNumber,
        String photoUrl,
        boolean emailVerified) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getBio(),
                user.getJobTitle(),
                user.getLocation(),
                user.getPhoneNumber(),
                user.getPhotoUrl(),
                user.isEmailVerified());
    }
}
