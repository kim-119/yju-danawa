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

    private final BookCommentRepository bookCommentRepository;
    private final BookCommentLikeRepository bookCommentLikeRepository;
    private final UserRepository userRepository;

    public BookCommentService(
            BookCommentRepository bookCommentRepository,
            BookCommentLikeRepository bookCommentLikeRepository,
            UserRepository userRepository
    ) {
        this.bookCommentRepository = bookCommentRepository;
        this.bookCommentLikeRepository = bookCommentLikeRepository;
        this.userRepository = userRepository;
    }

    public List<BookCommentDto> listComments(String rawIsbn13, String usernameOrNull) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        List<BookComment> comments = bookCommentRepository.findByIsbn13OrderByCreatedAtDesc(isbn13);
        if (comments.isEmpty()) {
            return List.of();
        }

        List<Long> commentIds = comments.stream().map(BookComment::getId).toList();

        Map<Long, Long> likeCounts = new HashMap<>();
        for (Object[] row : bookCommentLikeRepository.countByCommentIds(commentIds)) {
            Long commentId = (Long) row[0];
            Long count = (Long) row[1];
            likeCounts.put(commentId, count);
        }

        User me = resolveUser(usernameOrNull);
        Set<Long> likedCommentIds = Set.of();
        if (me != null) {
            likedCommentIds = new HashSet<>(bookCommentLikeRepository.findLikedCommentIds(commentIds, me.getUserId()));
        }

        final User currentUser = me;
        final Set<Long> finalLikedCommentIds = likedCommentIds;
        return comments.stream()
                .map(comment -> new BookCommentDto(
                        comment.getId(),
                        comment.getIsbn13(),
                        comment.getContent(),
                        comment.getUsername(),
                        comment.getCreatedAt().toString(),
                        likeCounts.getOrDefault(comment.getId(), 0L),
                        finalLikedCommentIds.contains(comment.getId()),
                        currentUser != null && Objects.equals(comment.getUser().getUserId(), currentUser.getUserId())
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public BookCommentDto createComment(String rawIsbn13, String content, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        String cleanContent = sanitizeContent(content);
        User user = requireUser(username);

        BookComment comment = new BookComment();
        comment.setIsbn13(isbn13);
        comment.setContent(cleanContent);
        comment.setUser(user);
        comment.setUsername(user.getUsername());
        comment.setCreatedAt(Instant.now());
        BookComment saved = bookCommentRepository.save(comment);

        return new BookCommentDto(
                saved.getId(),
                saved.getIsbn13(),
                saved.getContent(),
                saved.getUsername(),
                saved.getCreatedAt().toString(),
                0L,
                false,
                true
        );
    }

    @Transactional
    public void deleteComment(String rawIsbn13, Long commentId, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        User user = requireUser(username);

        BookComment comment = bookCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!isbn13.equals(comment.getIsbn13())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this book");
        }
        if (!Objects.equals(comment.getUser().getUserId(), user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this comment");
        }

        bookCommentRepository.delete(comment);
    }

    @Transactional
    public BookCommentLikeToggleResponse toggleLike(String rawIsbn13, Long commentId, String username) {
        String isbn13 = normalizeIsbn13(rawIsbn13);
        User user = requireUser(username);
        BookComment comment = bookCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        if (!isbn13.equals(comment.getIsbn13())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment does not belong to this book");
        }

        boolean liked;
        Optional<BookCommentLike> existing = bookCommentLikeRepository.findByCommentIdAndUserUserId(commentId, user.getUserId());
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
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private User resolveUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(username).orElse(null);
    }

    private String normalizeIsbn13(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isbn13 must be exactly 13 digits");
        }
        return digits;
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        if (trimmed.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is too long (max 1000)");
        }
        return trimmed;
    }
}
