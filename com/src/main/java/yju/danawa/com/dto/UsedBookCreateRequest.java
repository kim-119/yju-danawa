package yju.danawa.com.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UsedBookCreateRequest(
        @NotBlank String title,
        String author,
        @Min(0) Integer priceWon,
        String description,
        String isbn,
        String isbn13,
        String bookCondition,
        Long departmentId,
        String status
) {}
