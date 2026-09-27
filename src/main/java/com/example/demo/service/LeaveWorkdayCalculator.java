package com.example.demo.service;

import com.example.demo.entity.LeaveDayPart;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class LeaveWorkdayCalculator {
    private LeaveWorkdayCalculator() {}

    public static double calculate(LocalDate from, LocalDate to, LeaveDayPart fromPart, LeaveDayPart toPart) {
        if (from.isAfter(to)) return 0;
        fromPart = fromPart == null ? LeaveDayPart.FULL_DAY : fromPart;
        toPart = toPart == null ? LeaveDayPart.FULL_DAY : toPart;
        long weekdays = from.datesUntil(to.plusDays(1))
                .filter(LeaveWorkdayCalculator::isWorkday)
                .count();
        if (weekdays == 0) return 0;

        double days = weekdays;
        if (isWorkday(from) && fromPart != LeaveDayPart.FULL_DAY) days -= 0.5;
        if (!from.equals(to) && isWorkday(to) && toPart != LeaveDayPart.FULL_DAY) days -= 0.5;
        return days;
    }

    public static boolean isWorkday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
