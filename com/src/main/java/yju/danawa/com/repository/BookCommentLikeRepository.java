package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import yju.danawa.com.domain.BookCommentLike;

import java.util.List;
import java.util.Optional;

public interface BookCommentLikeRepository extends JpaRepository<BookCommentLike, Long> {
    Optional<BookCommentLike> findByCommentIdAndUserUserId(Long commentId, Long userId);

    long countByCommentId(Long commentId);

    @Query("select l.comment.id, count(l.id) from BookCommentLike l where l.comment.id in :commentIds group by l.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    @Query("select l.comment.id from BookCommentLike l where l.comment.id in :commentIds and l.user.userId = :userId")
    List<Long> findLikedCommentIds(@Param("commentIds") List<Long> commentIds, @Param("userId") Long userId);
}
