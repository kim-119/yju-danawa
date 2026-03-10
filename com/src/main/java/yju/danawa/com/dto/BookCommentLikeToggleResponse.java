package yju.danawa.com.dto;

public record BookCommentLikeToggleResponse(Long commentId, boolean liked, long likeCount) {}
