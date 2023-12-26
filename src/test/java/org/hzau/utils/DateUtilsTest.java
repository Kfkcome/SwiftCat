package org.hzau.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DateUtilsTest {

    final long ts = 1445412480000L;
    final String date = "Wed, 21 Oct 2015 07:28:00 GMT";

    @Test
    void testParseDateTimeGMT() {
        assertEquals(ts, DateUtils.parseDateTimeGMT(date));
    }

    @Test
    void testFormatDateTimeGMT() {
        assertEquals(date, DateUtils.formatDateTimeGMT(ts));
    }
}
