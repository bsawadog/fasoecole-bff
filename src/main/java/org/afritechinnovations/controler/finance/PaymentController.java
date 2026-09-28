package org.afritechinnovations.controler.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PaymentDto;
import org.afritechinnovations.service.finance.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/by-invoice/{invoiceId}")
    public List<PaymentDto> getByInvoice(@PathVariable Long invoiceId) {
        return paymentService.findByInvoice(invoiceId);
    }

    @GetMapping("/{id}")
    public PaymentDto getById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto create(@RequestBody PaymentDto dto) {
        return paymentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        paymentService.delete(id);
    }
}
