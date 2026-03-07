package yju.danawa.com.web;

import org.springframework.web.bind.annotation.*;
import yju.danawa.com.domain.CampusNotice;
import yju.danawa.com.service.CampusNoticeService;

import java.util.*;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final CampusNoticeService campusNoticeService;

    public NoticeController(CampusNoticeService campusNoticeService) {
        this.campusNoticeService = campusNoticeService;
    }

    @GetMapping("/banners")
    public List<Map<String, Object>> getBanners() {
        return campusNoticeService.getActiveBanners(5).stream()
                .map(this::toMap)
                .toList();
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        campusNoticeService.syncFromScraper();
        return Map.of("status", "ok");
    }

    private Map<String, Object> toMap(CampusNotice n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("boardId", n.getBoardId());
        map.put("title", n.getTitle());
        map.put("imageUrl", n.getImageUrl());
        map.put("linkUrl", n.getLinkUrl());
        map.put("postedDate", n.getPostedDate());
        map.put("active", n.isActive());
        return map;
    }
}

