package yju.danawa.com.web;

import yju.danawa.com.domain.User;
import yju.danawa.com.service.JwtService;
import yju.danawa.com.service.UserProfileService;
import yju.danawa.com.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserService userService,
                          UserProfileService userProfileService,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @GetMapping("/validate")
    public java.util.Map<String, Object> validate(@RequestParam(required = false) String username,
                                                   @RequestParam(required = false) String studentId,
                                                   @RequestParam(required = false) String password) {
        boolean usernameAvailable  = username  == null || username.isBlank()  || !userService.existsByUsername(username);
        boolean studentIdAvailable = studentId == null || studentId.isBlank() || !userService.existsByStudentId(studentId);
        boolean passwordAvailable  = password  == null || password.isBlank()  || !userService.isPasswordInUse(password, passwordEncoder);
        return java.util.Map.of(
                "usernameAvailable",  usernameAvailable,
                "studentIdAvailable", studentIdAvailable,
                "passwordAvailable",  passwordAvailable
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        boolean exists = userService.existsByUsername(request.username());
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_EXISTS");
        }
        if (userService.existsByStudentId(request.studentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "STUDENT_ID_EXISTS");
        }
        if (userService.isPasswordInUse(request.password(), passwordEncoder)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PASSWORD_IN_USE");
        }

        LocalDateTime now = LocalDateTime.now();
        User saved = userService.save(new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email(),
                request.fullName(),
                request.department(),
                request.studentId(),
                request.phone(),
                true,
                false,
                0,
                null,
                now,
                now
        ));
        String token = jwtService.generateToken(saved.getUsername());
        return new LoginResponse(saved.getUsername(), "ok", token, userProfileService.getRoleNames(saved.getUserId()));
    }

    /** 아이디 찾기: 학번 + 이름 → 마스킹된 아이디 반환 */
    @PostMapping("/find-username")
    public java.util.Map<String, String> findUsername(@Valid @RequestBody FindUsernameRequest request) {
        String username = userService.findUsernameByStudentIdAndFullName(request.studentId(), request.fullName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일치하는 계정을 찾을 수 없습니다."));
        return java.util.Map.of("maskedUsername", maskUsername(username));
    }

    /** 비밀번호 재설정: 아이디 + 학번 검증 후 새 비밀번호로 변경 */
    @PostMapping("/reset-password")
    public java.util.Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        boolean ok = userService.resetPassword(
                request.username(), request.studentId(), request.newPassword(), passwordEncoder);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아이디 또는 학번이 올바르지 않습니다.");
        }
        return java.util.Map.of("message", "비밀번호가 변경되었습니다.");
    }

    private String maskUsername(String username) {
        int len = username.length();
        if (len <= 1) return "*";
        if (len == 2) return username.charAt(0) + "*";
        int showStart = Math.max(1, (int) Math.ceil(len / 3.0));
        int maskLen   = Math.max(1, len - showStart - 1);
        return username.substring(0, showStart) + "*".repeat(maskLen) + username.charAt(len - 1);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (Boolean.FALSE.equals(user.getIsEnabled()) || Boolean.TRUE.equals(user.getIsLocked())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new LoginResponse(user.getUsername(), "ok", token, userProfileService.getRoleNames(user.getUserId()));
    }
}
