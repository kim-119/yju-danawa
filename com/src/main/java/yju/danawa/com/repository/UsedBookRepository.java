package yju.danawa.com.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import yju.danawa.com.domain.UsedBook;

import java.util.List;

public interface UsedBookRepository extends JpaRepository<UsedBook, Long> {

    Page<UsedBook> findByDepartmentId(Long departmentId, Pageable pageable);

    /** 특정 ISBN-13의 판매중 매물을 가격 오름차순으로 최대 5건 조회 */
    List<UsedBook> findTop5ByIsbn13AndStatusOrderByPriceWonAsc(String isbn13, String status);

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
