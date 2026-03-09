package yju.danawa.com.web;

import jakarta.validation.constraints.NotBlank;

public record FindUsernameRequest(
        @NotBlank String studentId,
        @NotBlank String fullName
) {}
