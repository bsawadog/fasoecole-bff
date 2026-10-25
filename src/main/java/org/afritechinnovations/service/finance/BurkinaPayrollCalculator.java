package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.PayrollCalculationDto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;

/** CGI 106, 111-113, 227-229; LF 2026 art.16 for teaching vacations.
 * Social contributions are actual supplied amounts, never inferred from a tax rate.
 * Cash allowances are already included in gross; benefits in kind are not cash payable.
 */
public final class BurkinaPayrollCalculator {
    private BurkinaPayrollCalculator() { }
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
    public static Result calculate(YearMonth month, BigDecimal gross, Input in) {
        if (month.isBefore(YearMonth.of(2026,1))) throw new IllegalArgumentException("Le barème automatique est disponible à compter de janvier 2026.");
        if (in == null || in.regime()==null || in.tpaExempt()==null || in.reason()==null || in.reason().isBlank() || in.reason().length()>300)
            throw new IllegalArgumentException("Renseignez le régime, le statut TPA de l’établissement et le motif de validation.");
        for (BigDecimal value : new BigDecimal[]{gross,in.baseSalary(),in.housing(),in.functionAllowance(),in.transport(),in.benefitsInKind(),in.pension(),in.otherSocial(),in.otherDeductions(),in.employerContributions()})
            if(value==null || value.signum()<0 || value.scale()>2 || value.precision()-value.scale()>10) throw new IllegalArgumentException("Les montants doivent être positifs ou nuls, avec deux décimales maximum.");
        if (in.familyCharges()<0 || in.familyCharges()>4) throw new IllegalArgumentException("Les charges familiales admissibles vont de 0 à 4.");
        if (in.baseSalary().add(in.housing()).add(in.functionAllowance()).add(in.transport()).compareTo(gross)>0)
            throw new IllegalArgumentException("Le salaire de base et les indemnités sont des composantes du brut déjà enregistré ; leur somme ne peut pas le dépasser.");
        BigDecimal social = in.pension().add(in.otherSocial());
        BigDecimal pension = in.pension().min(in.baseSalary().multiply(n("0.08")));
        BigDecimal professional = in.baseSalary().multiply(in.upperCategory()?n("0.20"):n("0.25"));
        BigDecimal exemptionGross = gross.add(in.benefitsInKind()).subtract(social).max(ZERO);
        BigDecimal exemptions = in.housing().min(exemptionGross.multiply(n("0.20"))).min(n("50000"))
                .add(in.functionAllowance().min(exemptionGross.multiply(n("0.05"))).min(n("30000")))
                .add(in.transport().min(exemptionGross.multiply(n("0.05"))).min(n("20000")));
        BigDecimal base = gross.add(in.benefitsInKind()).subtract(pension).subtract(professional).subtract(exemptions).max(ZERO);
        // Whole-FCFA tax amounts; keep the exact taxable base, rather than inventing an unverified base rounding rule.
        var brackets = new ArrayList<Bracket>();
        int[] limits = {30000,50000,80000,120000,170000,250000};
        String[] rates = {"0","0.121","0.139","0.157","0.184","0.217","0.25"};
        BigDecimal previous = ZERO, grossIuts = ZERO;
        for(int i=0;i<rates.length;i++) {
            BigDecimal to = i<limits.length?BigDecimal.valueOf(limits[i]):null;
            BigDecimal portion = (to==null?base:base.min(to)).subtract(previous).max(ZERO);
            BigDecimal tax=portion.multiply(n(rates[i]));
            brackets.add(new Bracket(previous,to,n(rates[i]).multiply(n("100")),money(tax)));
            grossIuts=grossIuts.add(tax); if(to!=null) previous=to;
        }
        BigDecimal reduction = new BigDecimal[]{ZERO,n("0.08"),n("0.10"),n("0.12"),n("0.14")}[in.familyCharges()];
        BigDecimal vacation = ZERO, iuts;
        if(in.regime()==Regime.TEACHING_VACATION) {
            if(social.signum()>0 || in.baseSalary().signum()>0 || exemptions.signum()>0 || in.familyCharges()>0 || in.benefitsInKind().signum()>0)
                throw new IllegalArgumentException("Les vacations d’enseignement utilisent leur montant brut et la retenue spécifique, sans abattement salarié ni charges familiales.");
            vacation=gross.multiply(n("0.02")).setScale(0,RoundingMode.HALF_UP);
            base=gross; pension=ZERO; professional=ZERO; exemptions=ZERO; grossIuts=ZERO; reduction=ZERO; brackets.clear(); iuts=ZERO;
        } else iuts=grossIuts.multiply(BigDecimal.ONE.subtract(reduction)).setScale(0,RoundingMode.HALF_UP);
        BigDecimal deductions=money(social.add(in.otherDeductions()).add(vacation));
        BigDecimal net=money(gross.subtract(iuts).subtract(deductions));
        if(net.signum()<0) throw new IllegalArgumentException("Les retenues dépassent la rémunération brute ; corrigez les montants.");
        BigDecimal tpaBase=in.regime()==Regime.TEACHING_VACATION?ZERO:gross.add(in.benefitsInKind());
        if(in.approvedManagementCenter()) tpaBase=tpaBase.multiply(n("0.80"));
        BigDecimal tpa=in.tpaExempt()?ZERO:tpaBase.multiply(n("0.03")).setScale(0,RoundingMode.HALF_UP);
        return new Result("BF-CGI-2026-v1",month.toString(),in,money(gross),money(pension),money(professional),money(exemptions),money(base),brackets,
                money(grossIuts),reduction.multiply(n("100")),money(iuts),money(vacation),deductions,net,money(tpaBase),money(tpa),
                money(gross.add(in.benefitsInKind()).add(in.employerContributions()).add(tpa)));
    }
}
