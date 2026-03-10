package yju.danawa.com.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import yju.danawa.com.dto.BookCommentCreateRequest;
import yju.danawa.com.dto.BookCommentDto;
import yju.danawa.com.dto.BookCommentLikeToggleResponse;
import yju.danawa.com.service.BookCommentService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/books/{isbn13}/comments")
public class BookCommentController {

    private final BookCommentService bookCommentService;

    public BookCommentController(BookCommentService bookCommentService) {
        this.bookCommentService = bookCommentService;
    }

    @GetMapping
    public List<BookCommentDto> listComments(@PathVariable String isbn13, Principal principal) {
        String username = principal != null ? principal.getName() : null;
        return bookCommentService.listComments(isbn13, username);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookCommentDto createComment(@PathVariable String isbn13,
                                       @Valid @RequestBody BookCommentCreateRequest request,
                                       Principal principal) {
        String username = principal != null ? principal.getName() : null;
        return bookCommentService.createComment(isbn13, request.content(), request.completionRate(), username);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable String isbn13, @PathVariable Long commentId, Principal principal) {
        String username = principal != null ? principal.getName() : null;
        bookCommentService.deleteComment(isbn13, commentId, username);
    }

    @PostMapping("/{commentId}/like")
    public BookCommentLikeToggleResponse toggleLike(@PathVariable String isbn13,
                                                    @PathVariable Long commentId,
                                                    Principal principal) {
        String username = principal != null ? principal.getName() : null;
        return bookCommentService.toggleLike(isbn13, commentId, username);
    }
}
