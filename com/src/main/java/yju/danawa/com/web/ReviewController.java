package yju.danawa.com.web;

import org.springframework.web.bind.annotation.*;
import yju.danawa.com.service.ReviewService;

import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{bookId}/difficulty-heatmap")
    public Map<Integer, Long> getDifficultyHeatmap(@PathVariable String bookId) {
        return reviewService.getDifficultyHeatmap(bookId);
    }
}
