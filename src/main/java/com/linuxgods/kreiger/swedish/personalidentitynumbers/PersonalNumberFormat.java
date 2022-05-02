package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.Requirement.*;

public class PersonalNumberFormat implements Cloneable {
    private static final String YEAR_IN_CENTURY = "[0-9]{2}";
    private static final String MONTH = "0[1-9]|1[012]";
    private static final String DAY_IN_MONTH_COORDINATION_OPTIONAL = "[06][1-9]|[1278][0-9]|[39][01]";
    private static final String BIRTH_NUMBER = "(?!000)[0-9]{3}"; // 001-999
    private static final String CHECKSUM = "[0-9]";

    // The oldest person who got a personal number when they were introduced in 1947
    // was born in 1840, making 1840 the earliest year in a personal number.
    public static final String CENTURY_WITH_MILLENNIUM = "(?:18(?=[4-9])|19|20)";
    public static final String CENTURY_WITH_OPTIONAL_MILLENNIUM = "(?:1?(?:8(?=[4-9])|9)|2?0)";
    public static final String CENTURY_DIGIT = "(?:8(?=[4-9])|[90])";
    public static final String SEPARATOR = "[-+]";
    private Requirement millennium = REQUIRED;
    private Requirement century = ALLOWED;
    private Requirement separator = ALLOWED;
    private Requirement checksumDigit = REQUIRED;

    private boolean invalidChecksumAllowed = false;
    private boolean surroundingDigitsAllowed = false;

    public static PersonalNumberFormat formatWithCentury(Requirement century) {
        return new PersonalNumberFormat().setCentury(century);
    }


    public String buildString() {
        String millennium = this.millennium.fold(CENTURY_WITH_MILLENNIUM, CENTURY_WITH_OPTIONAL_MILLENNIUM,
                CENTURY_DIGIT);
        String century = this.century.fold(millennium, millennium + "?", "");
        String year = century + YEAR_IN_CENTURY;
        String day = DAY_IN_MONTH_COORDINATION_OPTIONAL;
        String checksum = this.checksumDigit.fold(CHECKSUM, CHECKSUM + "?", "");

        String date = "((" + year + ")(" + MONTH + ")(" + day + "))";
        String separator = "(" + this.separator.fold(SEPARATOR, SEPARATOR + "?", "") + ")";
        String suffix = "(" + BIRTH_NUMBER + checksum + ")";
        String personalNumber = date
                + separator
                + suffix;

        personalNumber = surroundingDigitsAllowed ? "(?<![-+])" + personalNumber : "(?<![-+\\d])" + personalNumber + "(?!\\d)";
        return personalNumber;
    }

    public Requirement getMillennium() {
        return millennium;
    }

    public PersonalNumberFormat setMillennium(Requirement millennium) {
        this.millennium = millennium;
        return this;
    }

    public Requirement getCentury() {
        return century;
    }

    public PersonalNumberFormat setCentury(Requirement century) {
        this.century = century;
        return this;
    }

    public Requirement getSeparator() {
        return separator;
    }

    public PersonalNumberFormat setSeparator(Requirement separator) {
        this.separator = separator;
        return this;
    }

    public Requirement getChecksumDigit() {
        return checksumDigit;
    }

    public PersonalNumberFormat setChecksumDigit(Requirement checksumDigit) {
        this.checksumDigit = checksumDigit;
        return this;
    }

    public boolean isSurroundingDigitsAllowed() {
        return surroundingDigitsAllowed;
    }

    public PersonalNumberFormat setSurroundingDigitsAllowed(boolean surroundingDigitsAllowed) {
        this.surroundingDigitsAllowed = surroundingDigitsAllowed;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalNumberFormat)) return false;

        PersonalNumberFormat that = (PersonalNumberFormat) o;

        if (invalidChecksumAllowed != that.invalidChecksumAllowed) return false;
        if (surroundingDigitsAllowed != that.surroundingDigitsAllowed) return false;
        if (millennium != that.millennium) return false;
        if (century != that.century) return false;
        if (separator != that.separator) return false;
        return checksumDigit == that.checksumDigit;
    }

    @Override
    public int hashCode() {
        int result = millennium.hashCode();
        result = 31 * result + century.hashCode();
        result = 31 * result + separator.hashCode();
        result = 31 * result + checksumDigit.hashCode();
        result = 31 * result + (invalidChecksumAllowed ? 1 : 0);
        result = 31 * result + (surroundingDigitsAllowed ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        String format = formatString();
        String suffix = formatSuffix();
        format += suffix.isEmpty() ? "" : ", "+suffix;
        return format;
    }

    String formatSuffix() {
        List<String> suffix = new ArrayList<>();
        if (checksumDigit != REJECTED && invalidChecksumAllowed) {
            suffix.add("invalid checksum allowed");
        }
        if (surroundingDigitsAllowed) {
            suffix.add("surrounding digits allowed");
        }
        return String.join(", ", suffix);
    }

    @NotNull String formatString() {
        String century = this.century.fold(
                this.millennium.fold("YY", "Y?Y", "Y"),
                this.millennium.fold("(YY)?", "Y?Y?", "Y?"),
                "");
        String separator = this.separator.fold("-", "-?", "");
        String suffix = this.checksumDigit.fold("XXXC", "XXXC?", "XXX");
        return century + "YYMMDD" + separator + suffix;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    PersonalNumberFormat copy() {
        try {
            return (PersonalNumberFormat) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean isInvalidChecksumAllowed() {
        return invalidChecksumAllowed;
    }

    public PersonalNumberFormat setInvalidChecksumAllowed(boolean invalidChecksumAllowed) {
        this.invalidChecksumAllowed = invalidChecksumAllowed;
        return this;
    }

    public Predicate<PersonalNumberPatternMatch> getPredicate() {
        return invalidChecksumAllowed
                ? match -> true
                : match -> match.getChecksumDigit().isEmpty() || match.getChecksumDigit().equals("" + match.getCorrectChecksum());
    }
}
