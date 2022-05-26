package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.codeInspection.LocalQuickFix;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Year;

public class PersonalIdentityNumber implements Comparable<PersonalIdentityNumber> {
    static final Clock CLOCK = Clock.systemUTC();
    private final String personalNumber;
    public PersonalIdentityNumber(String personalNumber) {
        this.personalNumber = personalNumber;
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

    public boolean isCoordinationNumber() {
        return isCoordinationNumber(personalNumber);
    }

    public static boolean isCoordinationNumber(String personalNumber) {
        return personalNumber.charAt(6) >= 6;
    }

    @Override public int compareTo(@NotNull PersonalIdentityNumber o) {
        return personalNumber.compareTo(o.personalNumber);
    }

    public String formatLike(PersonalIdentityNumberPatternMatch match) {
        String yearString = personalNumber.substring(8 - match.getDate().length(), 8);
        String separator = "";
        if (match.hasSeparator()) {
            separator = getCorrectSeparator();
        }
        String suffix = match.hasChecksum() ? personalNumber.substring(8) : personalNumber.substring(8, 11);
        return yearString + separator + suffix;
    }

    @NotNull String getCorrectSeparator() {
        int year = Integer.parseInt(personalNumber.substring(0, 4));
        return Year.now(CLOCK).getValue() - year >= 100 ? "+" : "-";
    }

    public interface Fix {

        LocalQuickFix[] getQuickFixes();

        static Fix info(String message, LocalQuickFix... quickFixes) {
            return of(message, quickFixes);
        }

        static Fix weakWarning(String message, LocalQuickFix... quickFixes) {
            return of(message, quickFixes);
        }

        static Fix of(String message, LocalQuickFix... quickFixes) {
            return new Fix() {

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
