package yju.danawa.com.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.dto.ReviewDto;
import yju.danawa.com.service.ReviewService;
import yju.danawa.com.util.SecurityUtil;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class ReviewController {

    private final ReviewService reviewService;
    private final SecurityUtil securityUtil;

    public ReviewController(ReviewService reviewService, SecurityUtil securityUtil) {
        this.reviewService = reviewService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/{bookId}/reviews")
    public List<ReviewDto> getReviews(@PathVariable String bookId) {
        Long currentUserId = securityUtil.getCurrentUserId().orElse(null);
        return reviewService.getReviews(bookId, currentUserId);
    }

    @PostMapping("/{bookId}/reviews")
    public ReviewDto createReview(
            @PathVariable String bookId,
            @RequestBody ReviewCreateRequest request
    ) {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        
        // username을 SecurityUtil에서 가져오기 위해 SecurityUtil 업데이트 필요하거나 
        // 그냥 SecurityContextHolder에서 가져오기
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();

        return reviewService.createReview(bookId, userId, username, request.content(), request.rating());
    }

    @DeleteMapping("/{bookId}/reviews/{reviewId}")
    public void deleteReview(@PathVariable String bookId, @PathVariable Long reviewId) {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        try {
            reviewService.deleteReview(reviewId, userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping("/{bookId}/reviews/{reviewId}/like")
    public Map<String, Object> toggleLike(@PathVariable String bookId, @PathVariable Long reviewId) {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return reviewService.toggleLike(reviewId, userId);
    }

    @GetMapping("/{bookId}/difficulty-heatmap")
    public Map<Integer, Long> getDifficultyHeatmap(@PathVariable String bookId) {
        return reviewService.getDifficultyHeatmap(bookId);
    }

    public record ReviewCreateRequest(String content, Integer rating) {}
}
