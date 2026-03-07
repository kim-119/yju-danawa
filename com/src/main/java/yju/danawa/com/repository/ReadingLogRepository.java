package yju.danawa.com.repository;

import yju.danawa.com.domain.ReadingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReadingLogRepository extends JpaRepository<ReadingLog, Long> {

    List<ReadingLog> findByUserUserIdOrderByLogDateAsc(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReadingLog r WHERE r.id = :id AND r.user.userId = :userId")
    int deleteByIdAndUserUserId(@Param("id") Long id, @Param("userId") Long userId);
}
