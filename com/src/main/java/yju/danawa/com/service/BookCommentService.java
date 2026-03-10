package yju.danawa.com.service;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.domain.BookComment;
import yju.danawa.com.domain.BookCommentLike;
import yju.danawa.com.domain.User;
import yju.danawa.com.dto.BookCommentDto;
import yju.danawa.com.dto.BookCommentLikeToggleResponse;
import yju.danawa.com.repository.BookCommentLikeRepository;
import yju.danawa.com.repository.BookCommentRepository;
import yju.danawa.com.repository.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookCommentService {

    private static final Set<Integer> VALID_COMPLETION_RATES = Set.of(25, 50, 75, 100);

    private final BookCommentRepository bookCommentRepository;
    private final BookCommentLikeRepository bookCommentLikeRepository;
    private final UserRepository userRepository;

    public BookCommentService(BookCommentRepository bookCommentRepository,
                              BookCommentLikeRepository bookCommentLikeRepository,
                              UserRepository userRepository) {
        this.bookCommentRepository = bookCommentRepository;
        this.bookCommentLikeRepository = bookCommentLikeRepository;
        this.userRepository = userRepository;
    }

    public List<BookCommentDto> listComments(String rawIsbn13, String usernameOrNull) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        List<BookComment> comments = bookCommentRepository.findByIsbn13OrderByCreatedAtDesc(isbn13);
        if (comments.isEmpty()) return List.of();

        List<Long> commentIds = comments.stream().map(BookComment::getId).toList();

        Map<Long, Long> likeCounts = new HashMap<>();
        for (Object[] row : bookCommentLikeRepository.countByCommentIds(commentIds)) {
            likeCounts.put((Long) row[0], (Long) row[1]);
        }

        User me = resolveUser(usernameOrNull);
        Set<Long> likedIds = me == null ? Set.of()
                : new HashSet<>(bookCommentLikeRepository.findLikedCommentIds(commentIds, me.getUserId()));

        return comments.stream()
                .map(c -> new BookCommentDto(
                        c.getId(), c.getIsbn13(), c.getContent(), c.getUsername(),
                        c.getCreatedAt().toString(),
                        likeCounts.getOrDefault(c.getId(), 0L),
                        likedIds.contains(c.getId()),
                        me != null && Objects.equals(c.getUser().getUserId(), me.getUserId()),
                        c.getCompletionRate()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public BookCommentDto createComment(String rawIsbn13, String content, Integer completionRate, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        User user = requireUser(username);

        if (content == null || content.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        if (content.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is too long (max 1000)");
        if (completionRate != null && !VALID_COMPLETION_RATES.contains(completionRate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "완독률은 25, 50, 75, 100 중 하나여야 합니다.");

        BookComment comment = new BookComment();
        comment.setIsbn13(isbn13);
        comment.setContent(content.trim());
        comment.setCompletionRate(completionRate);
        comment.setUser(user);
        comment.setUsername(user.getUsername());
        comment.setCreatedAt(Instant.now());
        BookComment saved = bookCommentRepository.save(comment);

        return new BookCommentDto(saved.getId(), saved.getIsbn13(), saved.getContent(),
                saved.getUsername(), saved.getCreatedAt().toString(), 0L, false, true,
                saved.getCompletionRate());
    }

    @Transactional
    public void deleteComment(String rawIsbn13, Long commentId, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        User user = requireUser(username);
        BookComment comment = bookCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!isbn13.equals(comment.getIsbn13()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this book");
        if (!Objects.equals(comment.getUser().getUserId(), user.getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this comment");

        bookCommentRepository.delete(comment);
    }

    @Transactional
    public BookCommentLikeToggleResponse toggleLike(String rawIsbn13, Long commentId, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        User user = requireUser(username);
        BookComment comment = bookCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!isbn13.equals(comment.getIsbn13()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this book");

        Optional<BookCommentLike> existing = bookCommentLikeRepository.findByCommentIdAndUserUserId(commentId, user.getUserId());
        boolean liked;
        if (existing.isPresent()) {
            bookCommentLikeRepository.delete(existing.get());
            liked = false;
        } else {
            BookCommentLike like = new BookCommentLike();
            like.setComment(comment);
            like.setUser(user);
            like.setCreatedAt(Instant.now());
            bookCommentLikeRepository.save(like);
            liked = true;
        }
        long likeCount = bookCommentLikeRepository.countByCommentId(commentId);
        return new BookCommentLikeToggleResponse(commentId, liked, likeCount);
    }

    private User requireUser(String username) {
        if (username == null || username.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private User resolveUser(String username) {
        if (username == null || username.isBlank()) return null;
        return userRepository.findByUsernameIgnoreCase(username).orElse(null);
    }

    private String normalizeIsbn13(String raw) {
        if (raw == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 is required");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 13)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 must be exactly 13 digits");
        return digits;
    }
}
