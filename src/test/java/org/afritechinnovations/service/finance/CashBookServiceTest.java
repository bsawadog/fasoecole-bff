package org.afritechinnovations.service.finance;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CashBookServiceTest {
    @Test void moneyPreservesCentsAndRejectsRoundingNegativeAndOverflow() {
        assertEquals(new BigDecimal("100.50"),CashBookService.money(new BigDecimal("100.5"),true));
        assertEquals(new BigDecimal("0.00"),CashBookService.money(BigDecimal.ZERO,false));
        assertThrows(IllegalArgumentException.class,()->CashBookService.money(BigDecimal.ZERO,true));
        assertThrows(IllegalArgumentException.class,()->CashBookService.money(new BigDecimal("-1"),false));
        assertThrows(IllegalArgumentException.class,()->CashBookService.money(new BigDecimal("100.001"),true));
        assertThrows(IllegalArgumentException.class,()->CashBookService.money(new BigDecimal("10000000000"),true));
        assertThrows(IllegalArgumentException.class,()->CashBookService.money(null,false));
    }
    @Test void monthMustBeAnUnambiguousYearAndMonth() {
        assertEquals("2026-12",CashBookService.period("2026-12").toString());
        for(String invalid:new String[]{"2026-13","2026-1","2026-00","hello","2026-10-01"})
            assertThrows(IllegalArgumentException.class,()->CashBookService.period(invalid));
    }
}
