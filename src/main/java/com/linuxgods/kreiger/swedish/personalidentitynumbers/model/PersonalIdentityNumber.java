package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.TextRange;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.ReplaceQuickFix;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class PersonalIdentityNumber {
    static final Clock CLOCK = Clock.systemUTC();
    private final String personalNumber;
    private final Set<Fix> fixes;
    private final boolean checksumInvalid;
    private final boolean coordinationNumber;

    private PersonalIdentityNumber(String personalNumber, Set<Fix> fixes, boolean checksumInvalid, boolean coordinationNumber) {
        this.personalNumber = personalNumber;
        this.fixes = fixes;
        this.checksumInvalid = checksumInvalid;
        this.coordinationNumber = coordinationNumber;
    }

    static PersonalIdentityNumber of(PersonalIdentityNumberPatternMatch match) {

        Set<Fix> fixes = new HashSet<>();
        String fullDate = match.getDate();
        if (fullDate.length() == 7) {
            fullDate = addMillennium(fullDate);
            String digit = fullDate.substring(0, 1);
            fixes.add(Fix.weakWarning("Missing millennium digit '" + digit+"'", new ReplaceQuickFix("Add millennium digit", "Add millennium digit " + digit, TextRange.from(0, 0), digit)));
        } else if (fullDate.length() == 6) {
            String separator = match.getSeparator();
            fullDate = addCentury(fullDate, separator);
            if (separator.isEmpty()) {
                String digits = fullDate.substring(0, 2);
                fixes.add(Fix.info("Missing century digits '" + digits+"'", new ReplaceQuickFix("Add century digits", "Add century digits " + digits, TextRange.from(0, 0), digits)));
            }
        }
        String suffix = match.getSuffix();
        String personalNumber = fullDate + suffix;
        int checksum = match.getCorrectChecksum();
        boolean checksumInvalid = false;
        String original = match.getOriginal();
        String checksumDigit = match.getChecksumDigit();
        if (checksumDigit.isEmpty()) {
            personalNumber += checksum;
            fixes.add(Fix.weakWarning("Missing checksum digit '" + checksum+"'", new ReplaceQuickFix("Add checksum digit", "Add checksum digit " + checksum, TextRange.from(
                    original.length(), 0), "" + checksum)));
        } else if (!checksumDigit.equals("" + checksum)) {
            personalNumber = personalNumber.substring(0, 11) + checksum;
            fixes.add(Fix.weakWarning("Incorrect checksum digit '" + checksum+"'", new ReplaceQuickFix("Correct checksum digit", "Correct checksum digit " + checksum, TextRange.from(original.length() - 1, 1), "" + checksum)));
            checksumInvalid = true;
        }
        int year = match.getYear();
        int month = match.getMonth();
        YearMonth yearMonth = YearMonth.of(year, month);
        int lengthOfMonth = yearMonth.lengthOfMonth();
        int dayOfMonth = match.getDayOfMonth();
        if (dayOfMonth > lengthOfMonth) {
            String newDayOfMonth = String.format("%02d", lengthOfMonth);
            int dayStart = match.getDayStart();
            fixes.add(Fix.weakWarning("Only " + lengthOfMonth + " days in " + yearMonth, new ReplaceQuickFix("Invalid day of month", "Set day to " + newDayOfMonth, TextRange.from(
                    dayStart, 2), newDayOfMonth)));
        }

        return new PersonalIdentityNumber(personalNumber, fixes, checksumInvalid, match.isCoordinationNumber());
    }

    @NotNull private static String addCentury(String date, String separator) {
        int yearInCentury = Integer.parseInt(date.substring(0, 2));
        int century = (LocalDate.now(CLOCK).getYear() - yearInCentury) / 100;
        if ("+".equals(separator)) century -= 1;
        date = century + date;
        return date;
    }

    @NotNull private static String addMillennium(String date) {
        switch (date.charAt(0)) {
            case '8':
            case '9':
                return "1" + date;
            case '0':
                return "2" + date;
            default:
                throw new RuntimeException("Invalid date " + date);
        }
    }

    @Override
    public String toString() {
        return personalNumber;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalIdentityNumber)) return false;

        PersonalIdentityNumber that = (PersonalIdentityNumber) o;

        return personalNumber.equals(that.personalNumber);
    }

    @Override
    public int hashCode() {
        return personalNumber.hashCode();
    }

    public Set<Fix> getValidationErrors() {
        return fixes;
    }

    public boolean isChecksumInvalid() {
        return checksumInvalid;
    }

    public boolean isCoordinationNumber() {
        return coordinationNumber;
    }

    public interface Fix {

        default ProblemHighlightType getProblemHighlightType() {
            return ProblemHighlightType.INFORMATION;
        }

        LocalQuickFix[] getQuickFixes();

        static Fix info(String message, LocalQuickFix... quickFixes) {
            return of(ProblemHighlightType.INFORMATION, message, quickFixes);
        }

        static Fix weakWarning(String message, LocalQuickFix... quickFixes) {
            return of(ProblemHighlightType.WEAK_WARNING, message, quickFixes);
        }

        static Fix of(final ProblemHighlightType information, String message, LocalQuickFix... quickFixes) {
            return new Fix() {
                public ProblemHighlightType getProblemHighlightType() {
                    return information;
                }

                @Override
                public LocalQuickFix[] getQuickFixes() {
                    return quickFixes;
                }

                @Override
                public String toString() {
                    return message;
                }
            };
        }

    }
}
