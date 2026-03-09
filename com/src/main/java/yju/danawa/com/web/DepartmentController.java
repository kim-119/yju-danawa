package yju.danawa.com.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yju.danawa.com.dto.DepartmentDto;
import yju.danawa.com.repository.DepartmentRepository;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public List<DepartmentDto> getDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName()))
                .toList();
    }
}
