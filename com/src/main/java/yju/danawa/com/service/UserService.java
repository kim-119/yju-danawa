package yju.danawa.com.service;

import yju.danawa.com.domain.User;
import yju.danawa.com.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "users", key = "#username == null ? '' : #username.toLowerCase()")
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    @CacheEvict(cacheNames = "users", key = "#user.username == null ? '' : #user.username.toLowerCase()")
    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    public boolean existsByStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return false;
        }
        return userRepository.existsByStudentId(studentId.trim());
    }

    public boolean isPasswordInUse(String rawPassword, PasswordEncoder passwordEncoder) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        return userRepository.findAllPasswordHashes().stream()
                .anyMatch(hash -> passwordEncoder.matches(rawPassword, hash));
    }

    /** 아이디 찾기: 학번 + 이름 일치 시 username 반환 */
    public Optional<String> findUsernameByStudentIdAndFullName(String studentId, String fullName) {
        if (studentId == null || studentId.isBlank() || fullName == null || fullName.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByStudentIdAndFullName(studentId.trim(), fullName.trim())
                .map(User::getUsername);
    }

    /** 비밀번호 재설정: 아이디 + 학번 검증 후 새 비밀번호로 변경 */
    public boolean resetPassword(String username, String studentId, String newPassword,
                                 PasswordEncoder passwordEncoder) {
        if (username == null || studentId == null || newPassword == null) return false;
        return userRepository.findByUsernameAndStudentId(username.trim(), studentId.trim())
                .map(user -> {
                    user.setPassword(passwordEncoder.encode(newPassword));
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }
}
