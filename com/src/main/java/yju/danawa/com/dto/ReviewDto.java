package yju.danawa.com.dto;

import java.time.LocalDateTime;

public record ReviewDto(
        Long id,
        String bookId,
        Long userId,
        String username,
        String content,
        Integer rating,
        LocalDateTime createdAt,
        long likeCount,
        boolean likedByMe,
        boolean ownedByMe
) {
}
