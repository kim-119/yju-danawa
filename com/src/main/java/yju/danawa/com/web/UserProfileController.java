package yju.danawa.com.web;

import yju.danawa.com.dto.UserProfileDto;
import yju.danawa.com.service.UserProfileService;
import yju.danawa.com.dto.BookDto;
import yju.danawa.com.service.BookRecentlyViewedService;
import yju.danawa.com.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final BookRecentlyViewedService bookRecentlyViewedService;
    private final SecurityUtil securityUtil;

    public UserProfileController(UserProfileService userProfileService,
                                  BookRecentlyViewedService bookRecentlyViewedService,
                                  SecurityUtil securityUtil) {
        this.userProfileService = userProfileService;
        this.bookRecentlyViewedService = bookRecentlyViewedService;
        this.securityUtil = securityUtil;
    }

    @GetMapping("/me")
    public UserProfileDto getMyProfile() {
        String username = getAuthenticatedUsername();
        return userProfileService.getProfile(username);
    }

    @GetMapping("/me/recent-books")
    public List<BookDto> getMyRecentBooks() {
        Long userId = securityUtil.getCurrentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        return bookRecentlyViewedService.getRecentBooks(userId);
    }

    @GetMapping("/{username}")
    public UserProfileDto getUserProfile(@PathVariable String username) {
        String current = getAuthenticatedUsername();
        if (!current.equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        try {
            return userProfileService.getProfile(username);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return authentication.getName();
    }
}
