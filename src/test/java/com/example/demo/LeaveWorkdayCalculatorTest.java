package com.example.demo;

import com.example.demo.entity.LeaveDayPart;
import com.example.demo.service.LeaveWorkdayCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaveWorkdayCalculatorTest {
    @Test
    void singleHalfDayCountsAsHalf() {
        LocalDate friday = LocalDate.of(2026, 10, 9);

        assertEquals(0.5, LeaveWorkdayCalculator.calculate(
                friday, friday, LeaveDayPart.MORNING, LeaveDayPart.MORNING));
    }

    @Test
    void partialFirstAndLastDaysKeepFullIntermediateWeekdays() {
        assertEquals(2.0, LeaveWorkdayCalculator.calculate(
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14),
                LeaveDayPart.AFTERNOON, LeaveDayPart.MORNING));
    }

    @Test
    void weekendDaysDoNotCountAsWorkdays() {
        assertEquals(1.0, LeaveWorkdayCalculator.calculate(
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 11),
                LeaveDayPart.FULL_DAY, LeaveDayPart.FULL_DAY));
        assertEquals(0.5, LeaveWorkdayCalculator.calculate(
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 11),
                LeaveDayPart.MORNING, LeaveDayPart.FULL_DAY));
    }
}
