package yju.danawa.com.repository;

import yju.danawa.com.domain.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {

    Optional<Book> findByIsbn(String isbn);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query(value = "SELECT * FROM books b " +
            "WHERE REPLACE(LOWER(b.isbn), ' ', '') ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            "OR REPLACE(LOWER(b.title), ' ', '') ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            "OR REPLACE(LOWER(b.author), ' ', '') ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%') " +
            "OR REPLACE(LOWER(b.publisher), ' ', '') ILIKE concat('%', REPLACE(LOWER(:q), ' ', ''), '%')",
            nativeQuery = true)
    List<Book> searchByKeyword(@Param("q") String q);

    /**
     * 인기/추천 도서: 최신 등록순 상위 N건 (검색 결과 0건 Fallback용)
     */
    @Query(value = "SELECT * FROM books ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<Book> findPopularBooks(@Param("limit") int limit);

    /**
     * title_norm 기반 페이징 검색 (이미지 검색 서비스용)
     */
    @Query(value = "SELECT * FROM books " +
            "WHERE title_norm ILIKE concat('%', :q, '%') " +
            "ORDER BY similarity(title_norm, :q) DESC, isbn ASC",
            countQuery = "SELECT COUNT(*) FROM books WHERE title_norm ILIKE concat('%', :q, '%')",
            nativeQuery = true)
    Page<Book> searchByTitleNorm(@Param("q") String q, Pageable pageable);

    /**
     * 무한 스크롤을 위한 첫 페이지 조회
     * ISBN 오름차순으로 limit개 반환
     */
    @Query(value = "SELECT * FROM books ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findTopBooks(@Param("limit") int limit);

    /**
     * 무한 스크롤을 위한 다음 페이지 조회
     * cursor(ISBN)보다 큰 ISBN을 가진 도서를 limit개 반환
     */
    @Query(value = "SELECT * FROM books WHERE isbn > :cursor ORDER BY isbn ASC LIMIT :limit", nativeQuery = true)
    List<Book> findBooksAfterCursor(@Param("cursor") String cursor, @Param("limit") int limit);
}
