package yju.danawa.com.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import yju.danawa.com.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByName(String name);
}
