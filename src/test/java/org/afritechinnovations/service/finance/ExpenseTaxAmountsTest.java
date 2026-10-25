package org.afritechinnovations.service.finance;
import org.afritechinnovations.dto.finance.ExpenseTaxDetails;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ExpenseTaxAmountsTest {
 BigDecimal n(String v){return new BigDecimal(v);}
 ExpenseTaxDetails details(){return new ExpenseTaxDetails("F-001",null,"IFU-TEST",n("100000"),List.of(new ExpenseTaxDetails.Line("TVA",n("18000"))));}
 @Test void invoiceVatIsInsideTheGrossAmountAndSurvivesSerialization(){var d=details();assertDoesNotThrow(()->ExpenseTaxAmounts.validate(d,n("118000")));assertEquals(d,ExpenseTaxAmounts.read(ExpenseTaxAmounts.json(d)));assertThrows(IllegalArgumentException.class,()->ExpenseTaxAmounts.validate(d,n("100000")));}
 @Test void eachHalfPaymentContainsHalfTheVat(){var a=ExpenseTaxAmounts.portion(details(),n("118000"),n("0"),n("59000"));var b=ExpenseTaxAmounts.portion(details(),n("118000"),n("59000"),n("59000"));assertEquals(0,a.excludingTax().compareTo(n("50000")));assertEquals(0,a.taxes().getFirst().amount().compareTo(n("9000")));assertEquals(a,b);}
 @Test void centPaymentsAndMultipleTaxesNeverProduceNegativeHtOrDuplicateTax(){
 var lines=java.util.stream.IntStream.range(0,20).mapToObj(i->new ExpenseTaxDetails.Line("Tax "+i,n("0.01"))).toList();var d=new ExpenseTaxDetails(null,null,null,n("0.01"),lines);BigDecimal taxes=BigDecimal.ZERO,ht=BigDecimal.ZERO;
 for(int i=0;i<21;i++){var p=ExpenseTaxAmounts.portion(d,n("0.21"),n("0.01").multiply(BigDecimal.valueOf(i)),n("0.01"));assertTrue(p.excludingTax().signum()>=0);var sum=p.taxes().stream().map(ExpenseTaxDetails.Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add);assertTrue(p.taxes().stream().allMatch(t->t.amount().signum()>=0));assertEquals(0,sum.add(p.excludingTax()).compareTo(n("0.01")));taxes=taxes.add(sum);ht=ht.add(p.excludingTax());}
 assertEquals(0,taxes.compareTo(n("0.20")));assertEquals(0,ht.compareTo(n("0.01")));
 }
 @Test void rejectsOverpaymentsAndInvalidTaxAmounts(){assertThrows(IllegalArgumentException.class,()->ExpenseTaxAmounts.portion(details(),n("118000"),n("100000"),n("30000")));var d=new ExpenseTaxDetails(null,null,null,n("100"),List.of(new ExpenseTaxDetails.Line("TVA",n("0.001"))));assertThrows(IllegalArgumentException.class,()->ExpenseTaxAmounts.validate(d,n("100.001")));}
 @Test void absentTaxesKeepLegacyExpensesUnchanged(){assertDoesNotThrow(()->ExpenseTaxAmounts.validate(null,n("100")));assertNull(ExpenseTaxAmounts.portion(null,n("100"),BigDecimal.ZERO,n("50")));}
}
