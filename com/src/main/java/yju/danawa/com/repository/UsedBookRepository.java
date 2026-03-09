package yju.danawa.com.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import yju.danawa.com.domain.UsedBook;

public interface UsedBookRepository extends JpaRepository<UsedBook, Long> {

    Page<UsedBook> findByDepartmentId(Long departmentId, Pageable pageable);

    /**
     * 제목 ILIKE 부분검색 또는 ISBN 정확일치 검색 (PostgreSQL 전용).
     * 하이픈이 제거된 순수 숫자 ISBN도 함께 검색한다.
     */
    @Query(value = """
            SELECT * FROM used_books
             WHERE title ILIKE '%' || :titleQ || '%'
                OR isbn = :isbnQ
            """,
           countQuery = """
            SELECT COUNT(*) FROM used_books
             WHERE title ILIKE '%' || :titleQ || '%'
                OR isbn = :isbnQ
            """,
           nativeQuery = true)
    Page<UsedBook> searchByTitleOrIsbn(
            @Param("titleQ") String titleQ,
            @Param("isbnQ") String isbnQ,
            Pageable pageable);
}
