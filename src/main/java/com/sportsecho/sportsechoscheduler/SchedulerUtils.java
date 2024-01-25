package com.sportsecho.sportsechoscheduler;

import java.time.LocalDate;
import java.time.Month;
import org.springframework.stereotype.Service;

@Service
public class SchedulerUtils {

    public static String calculateSeason(String sport, LocalDate date) {
        // 스포츠별 시즌 시작 월 정의
        int seasonStartMonth;
        switch (sport) {
            case "EPL":
                seasonStartMonth = Month.AUGUST.getValue(); // 8월
                break;
            case "NBA":
                seasonStartMonth = Month.OCTOBER.getValue(); // 10월
                break;
            case "MLB":
                seasonStartMonth = Month.MARCH.getValue(); // 3월
                break;
            default:
                throw new IllegalArgumentException("Unknown sport: " + sport);
        }

        int currentYear = date.getYear();

        if (sport.equals("EPL")) {
            return String.valueOf(
                (date.getMonthValue() < seasonStartMonth) ? currentYear - 1 : currentYear);
        } else if (sport.equals("NBA")) {
            if (date.getMonthValue() < seasonStartMonth
                || date.getMonthValue() <= Month.APRIL.getValue()) {
                return (currentYear - 1) + "-" + currentYear;
            } else {
                return currentYear + "-" + (currentYear + 1);
            }
        } else {
            // MLB
            return String.valueOf(currentYear);
        }
    }

}
