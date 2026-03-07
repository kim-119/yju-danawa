package yju.danawa.com.repository;

import yju.danawa.com.domain.BookImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookImageRepository extends JpaRepository<BookImage, Long> {

    interface BookImageIsbnRow {
        Long getId();
        String getIsbn();
    }

    interface BookImageByIsbnRow {
        Long getId();
        String getIsbn13();
    }

    @Query(
            value = "SELECT id, book_isbn AS isbn FROM book_image WHERE book_isbn IN (:isbns) ORDER BY id ASC",
            nativeQuery = true)
    List<BookImageIsbnRow> findImageIdsByIsbns(@Param("isbns") List<String> isbns);

    @Query(value = """
            SELECT DISTINCT ON (bi.book_isbn)
                bi.id      AS id,
                bi.book_isbn AS isbn13
            FROM book_image bi
            WHERE bi.book_isbn IN (:isbn13List)
            ORDER BY bi.book_isbn, bi.id ASC
            """, nativeQuery = true)
    List<BookImageByIsbnRow> findFirstImageByIsbn13In(@Param("isbn13List") List<String> isbn13List);
}
