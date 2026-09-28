package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.SchoolUserDto;
import org.afritechinnovations.service.common.SchoolUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/school-users")
@RequiredArgsConstructor
public class SchoolUserController {

    private final SchoolUserService schoolUserService;

    @GetMapping("/by-school/{schoolId}")
    public List<SchoolUserDto> getBySchool(@PathVariable Long schoolId) {
        return schoolUserService.findBySchool(schoolId);
    }

    @GetMapping("/by-user/{userId}")
    public List<SchoolUserDto> getByUser(@PathVariable Long userId) {
        return schoolUserService.findByUser(userId);
    }

    @GetMapping("/{id}")
    public SchoolUserDto getById(@PathVariable Long id) {
        return schoolUserService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolUserDto create(@RequestBody SchoolUserDto dto) {
        return schoolUserService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schoolUserService.delete(id);
    }
}
