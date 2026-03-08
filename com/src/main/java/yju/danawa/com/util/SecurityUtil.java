package yju.danawa.com.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import yju.danawa.com.domain.User;
import yju.danawa.com.service.UserService;

import java.util.Optional;

@Component
public class SecurityUtil {

    private final UserService userService;

    public SecurityUtil(UserService userService) {
        this.userService = userService;
    }

    public Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return Optional.empty();
        }
        String username = (String) auth.getPrincipal();
        return userService.findByUsername(username).map(User::getUserId);
    }
}
