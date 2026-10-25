package org.afritechinnovations.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.dto.finance.OwnerExpenseDto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
public final class ExpenseInvoiceAmounts {
 private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
 private ExpenseInvoiceAmounts(){}
 public static BigDecimal withheld(Invoice invoice){return invoice.taxes().stream().filter(t->t.treatment()==TaxTreatment.WITHHELD).map(InvoiceTax::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
 public static void validate(Invoice invoice,BigDecimal paid,LocalDate paymentDate){
  if(invoice==null)return;
  if(invoice.number()==null||invoice.number().isBlank()||invoice.date()==null||invoice.date().isAfter(paymentDate)||invoice.taxes()==null)throw new IllegalArgumentException("Renseignez la facture et une date antérieure ou égale au paiement.");
  money(invoice.total());money(invoice.excludingTax());money(paid);
  BigDecimal included=BigDecimal.ZERO,withheld=BigDecimal.ZERO;
  for(var tax:invoice.taxes()){
   money(tax.amount());if(tax.amount().signum()<=0||tax.code()==null||tax.treatment()==null||tax.label()==null||tax.label().isBlank())throw new IllegalArgumentException("Renseignez chaque taxe et un montant positif.");
   if(tax.treatment()==TaxTreatment.INCLUDED)included=included.add(tax.amount());else withheld=withheld.add(tax.amount());
  }
  if(invoice.excludingTax().add(included).compareTo(invoice.total())!=0)throw new IllegalArgumentException("Le montant HT et les taxes facturées doivent correspondre au total TTC.");
  if(paid.add(withheld).compareTo(invoice.total())!=0||paid.signum()<=0)throw new IllegalArgumentException("Le net fournisseur doit être égal au TTC moins les retenues.");
  if(withheld.signum()>0&&(invoice.taxDueDate()==null||invoice.taxDueDate().isBefore(paymentDate)))throw new IllegalArgumentException("Renseignez l’échéance de reversement des retenues.");
 }
 private static void money(BigDecimal amount){if(amount==null||amount.signum()<0||amount.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Montant de facture invalide.");try{amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new IllegalArgumentException("Deux décimales maximum pour les taxes.");}}
 public static String json(Invoice invoice){if(invoice==null)return null;try{return JSON.writeValueAsString(invoice);}catch(Exception e){throw new IllegalArgumentException("Facture illisible.",e);}}
 public static Invoice read(String value){if(value==null)return null;try{return JSON.readValue(value,Invoice.class);}catch(Exception e){throw new IllegalStateException("Facture enregistrée illisible.",e);}}
}
