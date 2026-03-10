package yju.danawa.com.dto;

import java.util.List;

public record UsedBookDetailDto(
        Long id,
        String title,
        String author,
        Integer priceWon,
        String description,
        String sellerUsername,
        String isbn,
        String isbn13,
        String bookCondition,
        String status,
        Long departmentId,
        String departmentName,
        List<String> imageUrls,
        String createdAt
) {}
