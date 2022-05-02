package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class PersonalNumberFormats {
    private List<PersonalNumberFormat> formats;
    private Pattern pattern;

    private boolean coordinationNumber = true;

    public PersonalNumberFormats() {

    }

    public PersonalNumberFormats(List<PersonalNumberFormat> formats) {
        setFormats(new ArrayList<>(formats));
    }

    public Stream<PersonalNumberRange> ranges(CharSequence chars) {

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
                .map(PersonalNumberPatternMatch::new)
                .filter(match -> formats.get(match.getMatchedPatternIndex()).getPredicate().test(match))
                .flatMap(match -> Stream.of(PersonalNumber.of(match))
                        .filter(getPredicate())
                        .map(personalNumber -> new PersonalNumberRange(
                                personalNumber,
                                new TextRange(match.start(), match.end())))
                );
    }

    private static boolean matchingDelimiters(char first, char last) {
        return !Character.isLetterOrDigit(first) && first == last;
    }

    public List<PersonalNumberFormat> getFormats() {
        return formats;
    }

    public void setFormats(List<PersonalNumberFormat> formats) {
        this.formats = formats;
        this.pattern = Pattern.compile(formats.stream()
                .map(PersonalNumberFormat::buildString)
                .collect(joining("|", "(?:", ")")));
    }

    public boolean getCoordinationNumber() {
        return coordinationNumber;
    }

    public void setCoordinationNumber(boolean coordinationNumber) {
        this.coordinationNumber = coordinationNumber;
    }

    public Predicate<PersonalNumber> getPredicate() {
        return coordinationNumber
                ? pn -> true
                : personalNumber -> !personalNumber.isCoordinationNumber();
    }
}
