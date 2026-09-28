package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentDto;
import org.afritechinnovations.service.people.ParentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parents")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    @GetMapping("/{id}")
    public ParentDto getById(@PathVariable Long id) {
        return parentService.findById(id);
    }

    @GetMapping("/by-user/{userId}")
    public ParentDto getByUserId(@PathVariable Long userId) {
        return parentService.findByUserId(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParentDto create(@RequestBody ParentDto dto) {
        return parentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        parentService.delete(id);
    }
}