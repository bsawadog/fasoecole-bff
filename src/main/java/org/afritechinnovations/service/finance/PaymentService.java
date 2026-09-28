package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PaymentDto;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;

    public List<PaymentDto> findByInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public PaymentDto findById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable: " + id));
        return toDto(payment);
    }

    public PaymentDto create(PaymentDto dto) {
        Payment payment = Payment.builder()
                .invoice(Invoice.builder().id(dto.getInvoiceId()).build())
                .amount(dto.getAmount())
                .paymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : java.time.LocalDate.now())
                .method(dto.getMethod())
                .reference(dto.getReference())
                .build();
        Payment saved = paymentRepository.save(payment);
        updateInvoiceStatusIfFullyPaid(dto.getInvoiceId());
        return toDto(saved);
    }

    public void delete(Long id) {
        paymentRepository.deleteById(id);
    }

    private void updateInvoiceStatusIfFullyPaid(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }
        var totalPaid = paymentRepository.findByInvoiceId(invoiceId).stream()
                .map(Payment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (totalPaid.compareTo(invoice.getAmountDue()) >= 0) {
            invoice.setStatus(org.afritechinnovations.model.finance.InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
        }
    }

    private PaymentDto toDto(Payment payment) {
        return PaymentDto.builder()
                .id(payment.getId())
                .invoiceId(payment.getInvoice().getId())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .method(payment.getMethod())
                .reference(payment.getReference())
                .build();
    }
}
