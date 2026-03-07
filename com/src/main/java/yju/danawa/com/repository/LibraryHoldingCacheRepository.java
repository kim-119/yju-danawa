package yju.danawa.com.repository;

import yju.danawa.com.domain.LibraryHoldingCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface LibraryHoldingCacheRepository extends JpaRepository<LibraryHoldingCache, String> {

    Optional<LibraryHoldingCache> findByIsbn(String isbn);

    /** 특정 시각 이후에 체크된 유효한 캐시만 조회 */
    Optional<LibraryHoldingCache> findByIsbnAndCheckedAtAfter(String isbn, Instant after);
}

