package yju.danawa.com.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.domain.UsedBook;
import yju.danawa.com.dto.UsedBookCreateRequest;
import yju.danawa.com.dto.UsedBookDetailDto;
import yju.danawa.com.dto.UsedBookDto;
import yju.danawa.com.repository.UsedBookRepository;
import yju.danawa.com.service.UsedBookService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/used-books")
public class UsedBookController {

    private final UsedBookRepository usedBookRepository;
    private final UsedBookService usedBookService;

    public UsedBookController(UsedBookRepository usedBookRepository,
                              UsedBookService usedBookService) {
        this.usedBookRepository = usedBookRepository;
        this.usedBookService = usedBookService;
    }

    // ── 목록 조회 (기존 엔드포인트 유지) ───────────────────────────────────────

    @GetMapping
    public Map<String, Object> getUsedBooks(
            @RequestParam(required = false) Long department_id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UsedBook> result = department_id != null
                ? usedBookRepository.findByDepartmentId(department_id, pageable)
                : usedBookRepository.findAll(pageable);

        return Map.of(
                "items", result.getContent().stream().map(this::toDto).toList(),
                "total", result.getTotalElements(),
                "page", page,
                "size", safeSize
        );
    }

    // ── 단건 상세 조회 ─────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public UsedBookDetailDto getDetail(@PathVariable Long id) {
        return usedBookService.getDetail(id);
    }

    // ── 등록 (로그인 필수, multipart/form-data) ────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UsedBookDetailDto create(
            Principal principal,
            @RequestParam String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer priceWon,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String isbn13,
            @RequestParam(required = false) String bookCondition,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false, defaultValue = "AVAILABLE") String status,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        requireAuth(principal);
        UsedBookCreateRequest req = new UsedBookCreateRequest(
                title, author, priceWon, description, isbn, isbn13, bookCondition, departmentId, status);
        return usedBookService.create(principal.getName(), req, images);
    }

    // ── 수정 (작성자 본인만 가능) ──────────────────────────────────────────────

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsedBookDetailDto update(
            @PathVariable Long id,
            Principal principal,
            @RequestParam String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer priceWon,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String isbn13,
            @RequestParam(required = false) String bookCondition,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        requireAuth(principal);
        UsedBookCreateRequest req = new UsedBookCreateRequest(
                title, author, priceWon, description, isbn, isbn13, bookCondition, departmentId, status);
        return usedBookService.update(id, principal.getName(), req, images);
    }

    // ── 삭제 (작성자 본인만 가능) ──────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        requireAuth(principal);
        usedBookService.delete(id, principal.getName());
    }

    // ── 복합 검색: 제목(ILIKE) 또는 ISBN 정확일치 ─────────────────────────────

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query is required");
        }
        return usedBookService.search(q.trim(), page, size);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private void requireAuth(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
    }

    private UsedBookDto toDto(UsedBook book) {
        return new UsedBookDto(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPriceWon(),
                book.getDescription(),
                book.getSellerUsername(),
                book.getIsbn(),
                book.getIsbn13(),
                book.getBookCondition(),
                book.getStatus(),
                book.getImageUrl(),
                book.getDepartment() != null ? book.getDepartment().getId() : null,
                book.getDepartment() != null ? book.getDepartment().getName() : null,
                book.getCreatedAt() != null ? book.getCreatedAt().toString() : null
        );
    }
}
