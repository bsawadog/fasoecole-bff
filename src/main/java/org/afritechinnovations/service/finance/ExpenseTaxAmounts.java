package org.afritechinnovations.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.dto.finance.ExpenseTaxDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ExpenseTaxAmounts {
    private ExpenseTaxAmounts(){}
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    public static void validate(ExpenseTaxDetails details,BigDecimal total){
        if(details==null)return;
        if(details.taxes()==null||details.taxes().size()>20||details.excludingTax()==null||details.excludingTax().signum()<0)throw new IllegalArgumentException("Vérifiez le montant HT et les taxes facturées.");
        BigDecimal expected=details.excludingTax();
        exact(expected);
        for(var tax:details.taxes()){
            if(tax==null||tax.label()==null||tax.label().isBlank()||tax.label().length()>100||tax.amount()==null||tax.amount().signum()<=0)throw new IllegalArgumentException("Chaque taxe doit avoir un libellé et un montant positif.");
            exact(tax.amount());expected=expected.add(tax.amount());
        }
        if(total==null||expected.compareTo(total)!=0)throw new IllegalArgumentException("Le montant HT et les taxes doivent correspondre au montant total TTC.");
    }
    private static void exact(BigDecimal value){try{value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new IllegalArgumentException("Deux décimales maximum pour les taxes.");}}
    public static ExpenseTaxDetails portion(ExpenseTaxDetails details,BigDecimal total,BigDecimal paidBefore,BigDecimal payment){
        if(details==null)return null;
        validate(details,total);
        BigDecimal paidAfter=paidBefore.add(payment);
        if(total.signum()<=0||paidBefore.signum()<0||payment.signum()<=0||paidAfter.compareTo(total)>0)throw new IllegalArgumentException("Versement invalide pour la ventilation des taxes.");
        var after=allocated(details,total,paidAfter);var before=allocated(details,total,paidBefore);
        var taxes=new java.util.ArrayList<ExpenseTaxDetails.Line>();
        for(int i=0;i<details.taxes().size();i++)taxes.add(new ExpenseTaxDetails.Line(details.taxes().get(i).label(),after.get(i).subtract(before.get(i))));
        BigDecimal taxTotal=taxes.stream().map(ExpenseTaxDetails.Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ExpenseTaxDetails(details.invoiceNumber(),details.invoiceDate(),details.supplierIfu(),payment.subtract(taxTotal),taxes);
    }
    private static List<BigDecimal> allocated(ExpenseTaxDetails details,BigDecimal total,BigDecimal paid){
        var result=new java.util.ArrayList<BigDecimal>();BigDecimal remainingTotal=total,remainingPaid=paid;
        for(var tax:details.taxes()){
            BigDecimal amount=remainingTotal.signum()==0?BigDecimal.ZERO:remainingPaid.multiply(tax.amount()).divide(remainingTotal,2,RoundingMode.DOWN);
            result.add(amount);remainingPaid=remainingPaid.subtract(amount);remainingTotal=remainingTotal.subtract(tax.amount());
        }
        return result;
    }
    public static String json(ExpenseTaxDetails value){if(value==null)return null;try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Détail des taxes illisible.",e);}}
    public static ExpenseTaxDetails read(String value){if(value==null)return null;try{return JSON.readValue(value,ExpenseTaxDetails.class);}catch(Exception e){throw new IllegalStateException("Détail des taxes enregistré illisible.",e);}}
}
