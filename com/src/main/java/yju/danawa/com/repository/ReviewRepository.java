package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByBookIdOrderByCreatedAtDesc(String bookId);
}
