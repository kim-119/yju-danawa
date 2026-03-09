package yju.danawa.com.repository;

import yju.danawa.com.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByStudentId(String studentId);

    @Query("select u.password from User u")
    List<String> findAllPasswordHashes();

    /** 아이디 찾기: 학번 + 이름으로 사용자 조회 */
    @Query("SELECT u FROM User u WHERE u.studentId = :studentId AND LOWER(u.fullName) = LOWER(:fullName)")
    Optional<User> findByStudentIdAndFullName(@Param("studentId") String studentId,
                                              @Param("fullName") String fullName);

    /** 비밀번호 재설정: 아이디 + 학번 일치 여부 확인 */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username) AND u.studentId = :studentId")
    Optional<User> findByUsernameAndStudentId(@Param("username") String username,
                                              @Param("studentId") String studentId);
}
