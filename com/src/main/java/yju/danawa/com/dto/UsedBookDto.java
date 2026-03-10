package yju.danawa.com.dto;

public record UsedBookDto(
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
        String imageUrl,
        Long departmentId,
        String departmentName,
        String createdAt
) {}
