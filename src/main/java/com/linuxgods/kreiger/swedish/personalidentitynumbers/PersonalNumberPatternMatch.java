package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import java.util.regex.MatchResult;

public class PersonalNumberPatternMatch {
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

    PersonalNumberPatternMatch(MatchResult match) {
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

    private static int getMatchedPatternIndex(MatchResult match) {
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

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

}
