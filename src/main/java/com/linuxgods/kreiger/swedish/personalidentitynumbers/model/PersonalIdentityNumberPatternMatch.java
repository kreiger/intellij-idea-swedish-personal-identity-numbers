package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.openapi.util.TextRange;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.ReplaceQuickFix;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.MatchResult;

public class PersonalIdentityNumberPatternMatch {
    private static final int GROUP_COUNT = 6;
    private final String original;
    private final String date;
    private final int year;
    private final int month;
    private final int dayPart;
    private final String separator;
    private final String suffix;
    private final int matchedPatternIndex;
    private final int start;
    private final int end;
    private final int correctChecksum;
    private PersonalIdentityNumber personalIdentityNumber;
    private Set<PersonalIdentityNumber.Fix> fixes;

    PersonalIdentityNumberPatternMatch(MatchResult match) {
        this.start = match.start();
        this.end = match.end();
        this.original = match.group();
        this.matchedPatternIndex = getMatchedPatternIndex(match);
        int groupsStart = matchedPatternIndex*GROUP_COUNT;
        this.date = match.group(1 + groupsStart);
        this.year = Integer.parseInt(match.group(2 + groupsStart));
        this.month = Integer.parseInt(match.group(3 + groupsStart));
        this.dayPart = Integer.parseInt(match.group(4 + groupsStart));
        this.separator = match.group(5 + groupsStart);
        this.suffix = match.group(6 + groupsStart);
        this.correctChecksum = luhn(date.substring(date.length() - 6) + suffix.substring(0, 3));

    }

    public static int getMatchedPatternIndex(MatchResult match) {
        for (int i = 0, g = 1; g <= match.groupCount(); i++, g+=GROUP_COUNT) {
            if (match.start(g) != -1) return i;
        }
        throw new IllegalStateException();
    }

    public int getMatchedPatternIndex() {
        return matchedPatternIndex;
    }

    public String getOriginal() {
        return original;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDayOfMonth() {
        return isCoordinationNumber() ? dayPart - 60 : dayPart;
    }

    public String getSeparator() {
        return separator;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getDayStart() {
        return date.length() - 2;
    }

    public String getDate() {
        return date;
    }

    public boolean isCoordinationNumber() {
        return dayPart > 60;
    }

    public int getCorrectChecksum() {
        return correctChecksum;
    }

    public String getChecksumDigit() {
        return suffix.substring(3);
    }


    // Copied under MIT license from "dev.personnummer:personnummer" at
    // https://github.com/personnummer/java/blob/master/src/main/java/dev/personnummer/Personnummer.java
    private static int luhn(String value) {
        // Luhn/mod10 algorithm. Used to calculate a checksum from the
        // passed value. The checksum is returned and tested against the control number
        // in the personal identity number to make sure that it is a valid number.

        int temp;
        int sum = 0;

        for (int i = 0; i < value.length(); i++) {
            temp = Character.getNumericValue(value.charAt(i));
            temp *= 2 - (i % 2);
            if (temp > 9)
                temp -= 9;

            sum += temp;
        }

        return (10 - (sum % 10)) % 10;
    }

    public TextRange getTextRange() {
        return new TextRange(start, end);
    }
    public PersonalIdentityNumber getPersonalIdentityNumber() {
        if (null == personalIdentityNumber) {
            personalIdentityNumber = initPersonalIdentityNumber();
        }
        return personalIdentityNumber;
    }

    public Set<PersonalIdentityNumber.Fix> getFixes() {
        if (null == fixes) {
            fixes = initFixes();
        }
        return fixes;
    }

    private Set<PersonalIdentityNumber.Fix> initFixes() {
        Set<PersonalIdentityNumber.Fix> fixes = new HashSet<>();
        String date = getDate();
        String separator = getSeparator();
        if (date.length() == 6) {
            String centuryDigits = getCenturyDigits(date, separator);
            fixes.add(PersonalIdentityNumber.Fix.info("Missing century digits '" + centuryDigits +"'", new ReplaceQuickFix("Add century digits", "Add century digits '" + centuryDigits + "'", TextRange.from(0, 0), centuryDigits)));
        } else {
            if (date.length() == 7) {
                String millenniumDigit = getMillenniumDigit(date);
                fixes.add(PersonalIdentityNumber.Fix.weakWarning("Missing millennium digit '" + millenniumDigit + "'", new ReplaceQuickFix("Add millennium digit", "Add millennium digit '" + millenniumDigit + "'", TextRange.from(0, 0), millenniumDigit)));
            }
            if (!separator.isEmpty()) {
                String centuryDigits = date.substring(0, date.length() - 6);
                fixes.add(PersonalIdentityNumber.Fix.info("Remove century digits '" + centuryDigits +"'", new ReplaceQuickFix("Remove century digits", "Remove century digits '" + centuryDigits + "'", TextRange.from(0, centuryDigits.length()), "")));
                fixes.add(PersonalIdentityNumber.Fix.info("Remove separator '" + separator +"'", new ReplaceQuickFix("Remove separator", "Remove separator '" + separator + "'", TextRange.from(original.length() - suffix.length() - 1, 1), "")));
            }
        }
        if (separator.isEmpty()) {
            String sep = getPersonalIdentityNumber().getCorrectSeparator();
            fixes.add(PersonalIdentityNumber.Fix.info("Missing separator '" + sep +"'", new ReplaceQuickFix("Add separator", "Add separator '" + sep + "'", TextRange.from(original.length() - suffix.length(), 0), sep)));
        }
        int checksum = getCorrectChecksum();
        String original = getOriginal();
        String checksumDigit = getChecksumDigit();
        if (checksumDigit.isEmpty()) {
            fixes.add(PersonalIdentityNumber.Fix.weakWarning("Missing checksum digit '" + checksum+"'", new ReplaceQuickFix("Add checksum digit", "Add checksum digit '" + checksum + "'", TextRange.from(
                    original.length(), 0), "" + checksum)));
        } else if (!checksumDigit.equals("" + checksum)) {
            fixes.add(PersonalIdentityNumber.Fix.weakWarning("Incorrect checksum digit '" + checksum+"'", new ReplaceQuickFix("Correct checksum digit", "Correct checksum digit " + checksum, TextRange.from(original.length() - 1, 1), "" + checksum)));
        }
        YearMonth yearMonth = YearMonth.of(getYear(), getMonth());
        int lengthOfMonth = yearMonth.lengthOfMonth();
        int dayOfMonth = getDayOfMonth();
        if (dayOfMonth > lengthOfMonth) {
            String newDayOfMonth = String.format("%02d", lengthOfMonth);
            int dayStart = getDayStart();
            fixes.add(PersonalIdentityNumber.Fix.weakWarning("Only " + lengthOfMonth + " days in " + yearMonth, new ReplaceQuickFix("Invalid day of month", "Set day to " + newDayOfMonth, TextRange.from(
                    dayStart, 2), newDayOfMonth)));
        }

        return fixes;
    }

    @NotNull private PersonalIdentityNumber initPersonalIdentityNumber() {
        String fullDate = getFullDate();
        String correctSuffix = getCorrectSuffix();
        String personalNumber = fullDate + correctSuffix;

        return new PersonalIdentityNumber(personalNumber);
    }

    private String getCorrectSuffix() {
        String suffix = getSuffix();
        String checksumDigit = getChecksumDigit();
        int checksum = getCorrectChecksum();
        if (checksumDigit.isEmpty()) {
            suffix += checksum;
        } else if (!checksumDigit.equals("" + checksum)) {
            suffix = suffix.substring(0, 3) + checksum;
        }
        return suffix;
    }

    @NotNull private String getFullDate() {
        String date = getDate();
        switch (date.length()) {
            case 7:
                date = getMillenniumDigit(date) + date;
                break;
            case 6:
                date = getCenturyDigits(date, getSeparator()) + date;
                break;
        }
        return date;
    }

    @NotNull static String getMillenniumDigit(String date) {
        String millenniumDigit;
        switch (date.charAt(0)) {
            case '8':
            case '9':
                millenniumDigit = "1";
                break;
            case '0':
                millenniumDigit = "2";
                break;
            default:
                throw new RuntimeException("Invalid date " + date);
        }
        return millenniumDigit;
    }

    static String getCenturyDigits(String date, String separator) {
        int yearInCentury = Integer.parseInt(date.substring(0, 2));
        int century = (LocalDate.now(PersonalIdentityNumber.CLOCK).getYear() - yearInCentury) / 100;
        if ("+".equals(separator)) century -= 1;
        return ""+century;
    }

    public boolean hasSeparator() {
        return !separator.isEmpty();
    }

    public boolean hasChecksum() {
        return suffix.length() == 4;
    }
}
