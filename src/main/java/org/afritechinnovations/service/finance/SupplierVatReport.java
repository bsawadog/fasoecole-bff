package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.SchoolTaxDto.SupplierVat;
import org.afritechinnovations.dto.finance.OwnerExpenseDto.TaxTreatment;
import org.afritechinnovations.dto.finance.SchoolTaxDto.Code;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/** Invoice information only: no tax obligation, deduction eligibility or ledger write. */
public final class SupplierVatReport {
    private SupplierVatReport() { }
    static boolean isVat(String label) {
        String value=Normalizer.normalize(label,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace(".","").replaceAll("[^a-z0-9]+"," ").trim();
        return Arrays.asList(value.split(" ")).contains("tva") || value.equals("taxe sur la valeur ajoutee");
    }
    public static List<SupplierVat> read(JdbcTemplate jdbc,Long school,int year) {
        List<SupplierVat> lines=new ArrayList<>();
        // A payable is the complete invoice; its managed partial payments must not be counted again.
        jdbc.query("SELECT id,due_date,label,supplier,expense_taxes FROM school_payables WHERE school_id=? AND NOT cancelled AND source IN ('MANUAL','FIXED') AND expense_taxes IS NOT NULL",rs->{
            var taxes=ExpenseTaxAmounts.read(rs.getString("expense_taxes"));
            BigDecimal vat=taxes.taxes().stream().filter(t->isVat(t.label())).map(t->t.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            LocalDate date=taxes.invoiceDate()!=null?taxes.invoiceDate():rs.getDate("due_date").toLocalDate();
            if(date.getYear()==year && vat.signum()>0)lines.add(new SupplierVat("payable:"+rs.getLong("id"),date,taxes.invoiceDate()==null,rs.getString("label"),rs.getString("supplier"),taxes.invoiceNumber(),taxes.supplierIfu(),vat));
        },school);
        jdbc.query("SELECT id,expense_date,label,supplier,invoice_details FROM expenses WHERE school_id=? AND NOT managed_payment AND invoice_details IS NOT NULL",rs->{
            var invoice=ExpenseInvoiceAmounts.read(rs.getString("invoice_details"));
            BigDecimal vat=invoice.taxes().stream().filter(t->t.code()==Code.TVA && t.treatment()==TaxTreatment.INCLUDED).map(t->t.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
            LocalDate date=invoice.date()!=null?invoice.date():rs.getDate("expense_date").toLocalDate();
            if(date.getYear()==year && vat.signum()>0)lines.add(new SupplierVat("expense:"+rs.getLong("id"),date,invoice.date()==null,rs.getString("label"),rs.getString("supplier"),invoice.number(),invoice.supplierIfu(),vat));
        },school);
        return lines.stream().sorted(Comparator.comparing(SupplierVat::date).reversed().thenComparing(SupplierVat::key)).toList();
    }
}
