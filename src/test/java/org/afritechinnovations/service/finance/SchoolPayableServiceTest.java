package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.PayableDto;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class SchoolPayableServiceTest {
    private PayableDto.Row row(String status, String paid) {
        BigDecimal amount = new BigDecimal("100000.00"), already = new BigDecimal(paid);
        return new PayableDto.Row(1L,"MANUAL","2026-10",LocalDate.of(2026,10,5),"Loyer",null,1L,"Locaux",
                amount,already,amount.subtract(already),status,false,null);
    }
    @Test void partialAndFinalPaymentsHaveDistinctStatuses() {
        BigDecimal amount = new BigDecimal("100000");
        assertEquals("UNPAID",SchoolPayableService.status(false,amount,BigDecimal.ZERO));
        assertEquals("PARTIAL",SchoolPayableService.status(false,amount,new BigDecimal("40000")));
        assertEquals("PAID",SchoolPayableService.status(false,amount,amount));
        assertEquals("CANCELLED",SchoolPayableService.status(true,amount,BigDecimal.ZERO));
    }
    @Test void partialPaymentIsAcceptedButOverpaymentAndCancelledPaymentAreRejected() {
        assertDoesNotThrow(() -> SchoolPayableService.validatePayment(row("PARTIAL","40000"),new BigDecimal("60000")));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.validatePayment(row("PARTIAL","40000"),new BigDecimal("60000.01")));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.validatePayment(row("CANCELLED","0"),BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.validatePayment(row("UNPAID","0"),BigDecimal.ZERO));
    }
    @Test void amountsNeverSilentlyRoundToADifferentPayment() {
        assertEquals(new BigDecimal("100.50"),SchoolPayableService.money(new BigDecimal("100.5")));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.money(new BigDecimal("100.001")));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.money(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,() -> SchoolPayableService.money(new BigDecimal("10000000000")));
    }
}
