package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.InvoiceDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public List<InvoiceDto> findByStudent(Long studentId) {
        return invoiceRepository.findByStudentId(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<InvoiceDto> findByStatuses(List<InvoiceStatus> statuses) {
        return invoiceRepository.findAllWithDetailsByStatuses(statuses)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<InvoiceDto> findOverdueCandidates() {
        return invoiceRepository.findOverdueCandidates(LocalDate.now())
                .stream()
                .map(this::toDto)
                .toList();
    }

    public InvoiceDto findById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + id));
        return toDto(invoice);
    }

    public InvoiceDto create(InvoiceDto dto) {
        Invoice invoice = Invoice.builder()
                .student(Student.builder().id(dto.getStudentId()).build())
                .feeType(FeeType.builder().id(dto.getFeeTypeId()).build())
                .academicYear(AcademicYear.builder().id(dto.getAcademicYearId()).build())
                .amountDue(dto.getAmountDue())
                .dueDate(dto.getDueDate())
                .status(dto.getStatus() != null ? dto.getStatus() : InvoiceStatus.PENDING)
                .build();
        return toDto(invoiceRepository.save(invoice));
    }

    public InvoiceDto updateStatus(Long id, InvoiceStatus status) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + id));
        invoice.setStatus(status);
        return toDto(invoiceRepository.save(invoice));
    }

    public void delete(Long id) {
        invoiceRepository.deleteById(id);
    }

    private InvoiceDto toDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .studentId(invoice.getStudent().getId())
                .feeTypeId(invoice.getFeeType().getId())
                .academicYearId(invoice.getAcademicYear().getId())
                .amountDue(invoice.getAmountDue())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .build();
    }
}
