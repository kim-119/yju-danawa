package yju.danawa.com.dto;

public record BookCommentDto(
        Long id,
        String isbn13,
        String content,
        String username,
        String createdAt,
        long likeCount,
        boolean likedByMe,
        boolean ownedByMe
) {
}
