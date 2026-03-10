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

    /** 여러 댓글의 좋아요 수를 한 번에 집계 */
    @Query("SELECT l.comment.id, COUNT(l) FROM BookCommentLike l WHERE l.comment.id IN :commentIds GROUP BY l.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    /** 현재 사용자가 좋아요한 댓글 ID 목록 */
    @Query("SELECT l.comment.id FROM BookCommentLike l WHERE l.comment.id IN :commentIds AND l.user.userId = :userId")
    List<Long> findLikedCommentIds(@Param("commentIds") List<Long> commentIds, @Param("userId") Long userId);
}
