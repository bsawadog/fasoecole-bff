package org.afritechinnovations.controler.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.FeeTypeDto;
import org.afritechinnovations.service.finance.FeeTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fee-types")
@RequiredArgsConstructor
public class FeeTypeController {

    private final FeeTypeService feeTypeService;

    @GetMapping
    public List<FeeTypeDto> getBySchool(@RequestParam Long schoolId) {
        return feeTypeService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public FeeTypeDto getById(@PathVariable Long id) {
        return feeTypeService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeeTypeDto create(@RequestBody FeeTypeDto dto) {
        return feeTypeService.create(dto);
    }

    @PutMapping("/{id}")
    public FeeTypeDto update(@PathVariable Long id, @RequestBody FeeTypeDto dto) {
        return feeTypeService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        feeTypeService.delete(id);
    }
}
