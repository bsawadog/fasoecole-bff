package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.PayrollCalculationDto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BurkinaPayrollCalculatorTest {
    static final YearMonth MONTH=YearMonth.of(2026,10);
    static BigDecimal n(String value){return new BigDecimal(value);}
    static Input input(String base,int charges,boolean exempt){return new Input(Regime.SALARIED,n(base),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),false,charges,exempt,false,"Justificatifs vérifiés");}
    static void amount(String value,BigDecimal actual){assertEquals(0,n(value).compareTo(actual),actual.toString());}
    @ParameterizedTest @CsvSource({"30000,0","50000,2420","80000,6590","100000,9730","120000,12870","170000,22070","250000,39430","300000,51930"})
    void progressiveBracketBoundaries(String gross,String expected){amount(expected,BurkinaPayrollCalculator.calculate(MONTH,n(gross),input("0",0,true)).iuts());}
    @Test void familyReductionAppliesToTaxNotGross(){var r=BurkinaPayrollCalculator.calculate(MONTH,n("100000"),input("0",2,true));amount("8757",r.iuts());amount("91243",r.net());}
    @Test void professionalAllowanceAndRetirementCapDoNotReduceTheActualPensionDeduction(){
        var in=new Input(Regime.SALARIED,n("100000"),n("0"),n("0"),n("0"),n("0"),n("10000"),n("1000"),n("2000"),n("15000"),false,0,false,false,"Pension réelle");
        var r=BurkinaPayrollCalculator.calculate(MONTH,n("100000"),in);
        amount("8000",r.pensionDeductible());amount("25000",r.professionalAllowance());amount("67000",r.taxableBase());amount("4783",r.iuts());amount("82217",r.net());amount("118000",r.employerCost());
    }
    @Test void cashAllowancesAreNotAddedTwiceAndBenefitsAreNotPaidInCash(){
        var in=new Input(Regime.SALARIED,n("100000"),n("30000"),n("10000"),n("10000"),n("20000"),n("0"),n("0"),n("0"),n("0"),true,4,false,true,"Indemnités incluses");
        var r=BurkinaPayrollCalculator.calculate(MONTH,n("150000"),in);
        amount("47000",r.exemptions());amount("20000",r.professionalAllowance());amount("103000",r.taxableBase());amount("136000",r.tpaBase());amount("4080",r.tpa());amount("174080",r.employerCost());
        amount("150000",r.net().add(r.iuts()));
    }
    @Test void privateEducationExemptionDoesNotChangeNet(){var taxed=BurkinaPayrollCalculator.calculate(MONTH,n("100000"),input("100000",0,false));var exempt=BurkinaPayrollCalculator.calculate(MONTH,n("100000"),input("100000",0,true));amount("3000",taxed.tpa());amount("0",exempt.tpa());assertEquals(taxed.net(),exempt.net());}
    @Test void teachingVacationsUseSeparateWithholdingWithoutIutsOrTpa(){
        var in=new Input(Regime.TEACHING_VACATION,n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n("1000"),n("0"),false,0,false,false,"Vacation 2026");
        var r=BurkinaPayrollCalculator.calculate(MONTH,n("80000"),in);amount("1600",r.vacationWithholding());amount("0",r.iuts());amount("0",r.tpa());amount("77400",r.net());
    }
    @Test void rejectsInvalidCompositionAndUnsupportedPeriod(){assertThrows(IllegalArgumentException.class,()->BurkinaPayrollCalculator.calculate(MONTH,n("100000"),input("100001",0,true)));assertThrows(IllegalArgumentException.class,()->BurkinaPayrollCalculator.calculate(YearMonth.of(2025,12),n("100000"),input("100000",0,true)));}
    @Test void allocationPreservesTotalsForMultipleContractsAndTinyAmounts(){
        var parts=PayrollService.allocate(n("8757"),Map.of(1L,n("30000"),2L,n("80000")));amount("8757",parts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        var tiny=PayrollService.allocate(n("0.02"),Map.of(1L,n("0.01"),2L,n("0.01"),3L,n("0.01")));amount("0.02",tiny.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));assertTrue(tiny.values().stream().allMatch(v->v.compareTo(n("0.01"))<=0));
    }
}
