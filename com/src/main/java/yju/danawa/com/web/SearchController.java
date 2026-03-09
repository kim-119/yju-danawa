package yju.danawa.com.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yju.danawa.com.service.PopularSearchService;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final PopularSearchService popularSearchService;

    public SearchController(PopularSearchService popularSearchService) {
        this.popularSearchService = popularSearchService;
    }

    /**
     * 전체 통합 인기 검색어 순위 1~10위 반환.
     * 응답: [{rank, keyword, count}, ...]
     */
    @GetMapping("/popular")
    public List<PopularSearchService.RankEntry> getPopularKeywords() {
        return popularSearchService.getTop10();
    }
}
