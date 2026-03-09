package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.UsedBookImage;

import java.util.List;

public interface UsedBookImageRepository extends JpaRepository<UsedBookImage, Long> {
    List<UsedBookImage> findByUsedBookId(Long usedBookId);
    void deleteByUsedBookId(Long usedBookId);
}
