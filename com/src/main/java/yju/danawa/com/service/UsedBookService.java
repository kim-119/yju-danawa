package yju.danawa.com.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import yju.danawa.com.domain.Department;
import yju.danawa.com.domain.UsedBook;
import yju.danawa.com.domain.UsedBookImage;
import yju.danawa.com.domain.User;
import yju.danawa.com.dto.UsedBookCreateRequest;
import yju.danawa.com.dto.UsedBookDetailDto;
import yju.danawa.com.dto.UsedBookDto;
import yju.danawa.com.repository.DepartmentRepository;
import yju.danawa.com.repository.UsedBookImageRepository;
import yju.danawa.com.repository.UsedBookRepository;
import yju.danawa.com.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UsedBookService {

    private final UsedBookRepository usedBookRepository;
    private final UsedBookImageRepository usedBookImageRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PopularSearchService popularSearchService;
    private final Path uploadDir;

    public UsedBookService(
            UsedBookRepository usedBookRepository,
            UsedBookImageRepository usedBookImageRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            PopularSearchService popularSearchService,
            @Value("${app.upload.directory:./uploads/images}") String uploadDirectory) {
        this.usedBookRepository = usedBookRepository;
        this.usedBookImageRepository = usedBookImageRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.popularSearchService = popularSearchService;
        this.uploadDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + uploadDirectory, e);
        }
    }

    @Transactional
    public UsedBookDetailDto create(String username, UsedBookCreateRequest req, List<MultipartFile> files) {
        User seller = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Department dept = resolveDepartment(req.departmentId());

        UsedBook book = new UsedBook();
        book.setTitle(req.title());
        book.setAuthor(req.author());
        book.setPriceWon(req.priceWon());
        book.setDescription(req.description());
        book.setIsbn(req.isbn());
        book.setIsbn13(req.isbn13());
        book.setBookCondition(req.bookCondition());
        book.setStatus(req.status() != null ? req.status() : "AVAILABLE");
        book.setSeller(seller);
        book.setSellerUsername(seller.getUsername());
        book.setDepartment(dept);
        book.setCreatedAt(Instant.now());

        UsedBook saved = usedBookRepository.save(book);
        List<String> imageUrls = saveImages(saved, files);

        if (!imageUrls.isEmpty()) {
            saved.setImageUrl(imageUrls.get(0));
            usedBookRepository.save(saved);
        }

        return toDetailDto(saved, imageUrls);
    }

    public UsedBookDetailDto getDetail(Long id) {
        UsedBook book = findByIdOrThrow(id);
        List<String> imageUrls = usedBookImageRepository.findByUsedBookId(id)
                .stream().map(UsedBookImage::getFileUrl).toList();
        return toDetailDto(book, imageUrls);
    }

    @Transactional
    public UsedBookDetailDto update(Long id, String username, UsedBookCreateRequest req, List<MultipartFile> files) {
        UsedBook book = findByIdOrThrow(id);
        verifyOwner(book, username);

        book.setTitle(req.title());
        book.setAuthor(req.author());
        book.setPriceWon(req.priceWon());
        book.setDescription(req.description());
        book.setIsbn(req.isbn());
        if (req.isbn13() != null) book.setIsbn13(req.isbn13());
        if (req.bookCondition() != null) book.setBookCondition(req.bookCondition());
        book.setStatus(req.status() != null ? req.status() : book.getStatus());
        book.setDepartment(req.departmentId() != null ? resolveDepartment(req.departmentId()) : book.getDepartment());

        if (files != null && !files.isEmpty()) {
            usedBookImageRepository.deleteByUsedBookId(id);
            List<String> newUrls = saveImages(book, files);
            book.setImageUrl(newUrls.isEmpty() ? null : newUrls.get(0));
        }

        UsedBook saved = usedBookRepository.save(book);
        List<String> imageUrls = usedBookImageRepository.findByUsedBookId(id)
                .stream().map(UsedBookImage::getFileUrl).toList();
        return toDetailDto(saved, imageUrls);
    }

    @Transactional
    public void delete(Long id, String username) {
        UsedBook book = findByIdOrThrow(id);
        verifyOwner(book, username);
        usedBookImageRepository.deleteByUsedBookId(id);
        usedBookRepository.delete(book);
    }

    public Map<String, Object> search(String query, int page, int size) {
        popularSearchService.record(query);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedIsbn = query.replaceAll("-", "");
        Page<UsedBook> result = usedBookRepository.searchByTitleOrIsbn(query, normalizedIsbn, pageable);
        return Map.of(
                "items", result.getContent().stream().map(this::toSimpleDto).toList(),
                "total", result.getTotalElements(),
                "page", page,
                "size", safeSize
        );
    }

    /** 특정 ISBN-13 기반 실시간 최저가 매물 랭킹 조회 */
    public List<UsedBookDto> getUsedOffers(String isbn13) {
        List<UsedBook> offers = usedBookRepository.findTop5ByIsbn13AndStatusOrderByPriceWonAsc(isbn13, "AVAILABLE");
        return offers.stream().map(this::toSimpleDto).toList();
    }

    // --- helpers ---

    private UsedBook findByIdOrThrow(Long id) {
        return usedBookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Used book not found"));
    }

    private void verifyOwner(UsedBook book, String username) {
        if (!book.getSellerUsername().equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) return null;
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department not found"));
    }

    private List<String> saveImages(UsedBook book, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null || files.isEmpty()) return urls;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            String contentType = resolveContentType(file);
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only image files are allowed. Got: " + contentType);
            }

            String ext = extractExtension(file.getOriginalFilename(), contentType);
            String uniqueName = UUID.randomUUID() + ext;
            Path target = uploadDir.resolve(uniqueName);

            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save image");
            }

            String fileUrl = "/uploads/images/" + uniqueName;
            usedBookImageRepository.save(new UsedBookImage(book, uniqueName, fileUrl));
            urls.add(fileUrl);
        }
        return urls;
    }

    private String resolveContentType(MultipartFile file) {
        String declared = file.getContentType();
        if (declared != null && declared.startsWith("image/")) return declared;
        // Fallback: detect from magic bytes
        try (InputStream is = file.getInputStream()) {
            String guessed = URLConnection.guessContentTypeFromStream(is);
            if (guessed != null) return guessed;
        } catch (IOException ignored) {}
        return declared;
    }

    private String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/gif"  -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp"  -> ".bmp";
            default -> "";
        };
    }

    private UsedBookDetailDto toDetailDto(UsedBook book, List<String> imageUrls) {
        return new UsedBookDetailDto(
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
                book.getDepartment() != null ? book.getDepartment().getId() : null,
                book.getDepartment() != null ? book.getDepartment().getName() : null,
                imageUrls,
                book.getCreatedAt() != null ? book.getCreatedAt().toString() : null
        );
    }

    private UsedBookDto toSimpleDto(UsedBook book) {
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
