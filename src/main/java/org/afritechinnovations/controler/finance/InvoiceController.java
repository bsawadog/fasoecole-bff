package org.afritechinnovations.controler.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.InvoiceDto;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.service.finance.InvoiceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/by-student/{studentId}")
    public List<InvoiceDto> getByStudent(@PathVariable Long studentId) {
        return invoiceService.findByStudent(studentId);
    }

    @GetMapping
    public List<InvoiceDto> getByStatuses(@RequestParam List<InvoiceStatus> statuses) {
        return invoiceService.findByStatuses(statuses);
    }

    @GetMapping("/overdue-candidates")
    public List<InvoiceDto> getOverdueCandidates() {
        return invoiceService.findOverdueCandidates();
    }

    @GetMapping("/{id}")
    public InvoiceDto getById(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDto create(@RequestBody InvoiceDto dto) {
        return invoiceService.create(dto);
    }

    @PatchMapping("/{id}/status")
    public InvoiceDto updateStatus(@PathVariable Long id, @RequestParam InvoiceStatus status) {
        return invoiceService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoiceService.delete(id);
    }
}
