package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.CampusNotice;

import java.util.List;
import java.util.Optional;

public interface CampusNoticeRepository extends JpaRepository<CampusNotice, Long> {

    /** 활성 배너를 게시일 최신순으로 조회 (배너용) */
    List<CampusNotice> findByActiveTrueOrderByCrawledAtDesc();

    /** boardId로 기존 데이터 조회 (UPSERT용) */
    Optional<CampusNotice> findByBoardId(String boardId);
}

