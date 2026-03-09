package yju.danawa.com.dto;

import java.time.LocalDateTime;

public record CartItemDto(
        Long id,
        String bookId,
        String title,
        String author,
        String imageUrl,
        Double price,
        Integer quantity,
        LocalDateTime createdAt
) {
}
