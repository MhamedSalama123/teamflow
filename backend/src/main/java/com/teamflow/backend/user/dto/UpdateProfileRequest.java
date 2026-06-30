package com.teamflow.backend.user.dto;

import jakarta.validation.constraints.Size;

/** All fields are optional; a null value clears that field. */
public record UpdateProfileRequest(
        @Size(max = 150) String fullName,
        @Size(max = 1000) String bio,
        @Size(max = 150) String jobTitle,
        @Size(max = 150) String location,
        @Size(max = 40) String phoneNumber) {
}
