package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import yju.danawa.com.domain.BookComment;

import java.util.List;

public interface BookCommentRepository extends JpaRepository<BookComment, Long> {

    List<BookComment> findByIsbn13OrderByCreatedAtDesc(String isbn13);

    @Query("SELECT c.completionRate, COUNT(c) FROM BookComment c " +
           "WHERE c.isbn13 = :isbn13 AND c.completionRate IS NOT NULL " +
           "GROUP BY c.completionRate")
    List<Object[]> findCompletionRateStatsByIsbn13(@Param("isbn13") String isbn13);
}
