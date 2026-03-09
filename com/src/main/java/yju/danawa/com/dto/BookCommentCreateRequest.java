package yju.danawa.com.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookCommentCreateRequest(
        @NotBlank
        @Size(max = 1000)
        String content
) {
}
