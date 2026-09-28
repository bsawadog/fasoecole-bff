package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.LevelDto;
import org.afritechinnovations.service.academic.LevelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @GetMapping
    public List<LevelDto> getBySchool(@RequestParam Long schoolId) {
        return levelService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public LevelDto getById(@PathVariable Long id) {
        return levelService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LevelDto create(@RequestBody LevelDto dto) {
        return levelService.create(dto);
    }

    @PutMapping("/{id}")
    public LevelDto update(@PathVariable Long id, @RequestBody LevelDto dto) {
        return levelService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        levelService.delete(id);
    }
}
