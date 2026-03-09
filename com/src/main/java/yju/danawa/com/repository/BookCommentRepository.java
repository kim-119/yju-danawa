package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.BookComment;

import java.util.List;

public interface BookCommentRepository extends JpaRepository<BookComment, Long> {
    List<BookComment> findByIsbn13OrderByCreatedAtDesc(String isbn13);
}
