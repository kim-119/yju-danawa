package yju.danawa.com.web;

import yju.danawa.com.domain.ReadingLog;
import yju.danawa.com.domain.User;
import yju.danawa.com.repository.ReadingLogRepository;
import yju.danawa.com.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reading-logs")
public class ReadingLogController {

    private final ReadingLogRepository readingLogRepository;
    private final UserService userService;

    public ReadingLogController(ReadingLogRepository readingLogRepository, UserService userService) {
        this.readingLogRepository = readingLogRepository;
        this.userService = userService;
    }

    /** GET /api/reading-logs — 내 독서 기록 전체 조회 */
    @GetMapping
    public List<ReadingLogResponse> getMyLogs(Principal principal) {
        User user = resolveUser(principal);
        return readingLogRepository.findByUserUserIdOrderByLogDateAsc(user.getUserId())
                .stream()
                .map(ReadingLogResponse::from)
                .toList();
    }

    /** POST /api/reading-logs — 새 독서 기록 저장 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingLogResponse create(@Valid @RequestBody ReadingLogRequest req, Principal principal) {
        User user = resolveUser(principal);
        ReadingLog log = new ReadingLog(
                user,
                req.bookTitle(),
                req.isbn(),
                req.pagesRead(),
                req.memo(),
                req.logDate(),
                LocalDateTime.now()
        );
        return ReadingLogResponse.from(readingLogRepository.save(log));
    }

    /** DELETE /api/reading-logs/{id} — 본인 기록만 삭제 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        User user = resolveUser(principal);
        int deleted = readingLogRepository.deleteByIdAndUserUserId(id, user.getUserId());
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found or not owned by user");
        }
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────

    private User resolveUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // ─── DTO (Request / Response) ───────────────────────────────────

    public record ReadingLogRequest(
            @NotBlank String bookTitle,
            String isbn,
            Integer pagesRead,
            String memo,
            @NotNull LocalDate logDate
    ) {}

    public record ReadingLogResponse(
            Long id,
            String bookTitle,
            String isbn,
            Integer pagesRead,
            String memo,
            LocalDate logDate,
            LocalDateTime createdAt
    ) {
        static ReadingLogResponse from(ReadingLog log) {
            return new ReadingLogResponse(
                    log.getId(),
                    log.getBookTitle(),
                    log.getIsbn(),
                    log.getPagesRead(),
                    log.getMemo(),
                    log.getLogDate(),
                    log.getCreatedAt()
            );
        }
    }
}
