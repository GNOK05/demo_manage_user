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
        int sessions = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!isWorkday(date)) continue;
            int firstSession = date.equals(from) && fromPart == LeaveDayPart.AFTERNOON ? 1 : 0;
            int lastSession = date.equals(to) && toPart == LeaveDayPart.MORNING ? 0 : 1;
            if (firstSession <= lastSession) sessions += lastSession - firstSession + 1;
        }
        return sessions / 2.0;
    }

    public static boolean isWorkday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
