package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class PersonalIdentityNumberFormats {
    private List<PersonalIdentityNumberFormat> formats;
    private Pattern pattern;

    private boolean coordinationNumber = true;

    public PersonalIdentityNumberFormats() {

    }

    public PersonalIdentityNumberFormats(List<PersonalIdentityNumberFormat> formats) {
        setFormats(new ArrayList<>(formats));
    }

    public Stream<PersonalIdentityNumberPatternMatch> ranges(CharSequence chars) {

        return pattern.matcher(chars)
                .results()
                .filter(match -> {
                    int length = match.group().length();
                    char first = chars.charAt(0);
                    char last = chars.charAt(chars.length() - 1);
                    switch (chars.length() - length) {
                        case 1:
                            return match.start() == 1 && !Character.isLetterOrDigit(first)
                                    || !Character.isLetterOrDigit(last);
                        case 2:
                            return match.start() == 1 && matchingDelimiters(first, last);
                        default:
                            return true;
                    }
                })
                .map(PersonalIdentityNumberPatternMatch::new)
                .filter(match -> formats.get(match.getMatchedPatternIndex()).getPredicate().test(match))
                .filter(getPredicate());
    }

    private static boolean matchingDelimiters(char first, char last) {
        return !Character.isLetterOrDigit(first) && first == last;
    }

    public List<PersonalIdentityNumberFormat> getFormats() {
        return formats;
    }

    public void setFormats(List<PersonalIdentityNumberFormat> formats) {
        this.formats = formats;
        this.pattern = Pattern.compile(formats.stream()
                .map(PersonalIdentityNumberFormat::buildString)
                .collect(joining("|", "(?:", ")")));
    }

    public boolean getCoordinationNumber() {
        return coordinationNumber;
    }

    public void setCoordinationNumber(boolean coordinationNumber) {
        this.coordinationNumber = coordinationNumber;
    }

    public Predicate<PersonalIdentityNumberPatternMatch> getPredicate() {
        return coordinationNumber
                ? pn -> true
                : personalNumber -> !personalNumber.isCoordinationNumber();
    }
}
