package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.openapi.util.TextRange;

public class PersonalNumberRange extends TextRanged<PersonalNumber> {
    public PersonalNumberRange(PersonalNumber personalNumber, TextRange textRange) {
        super(personalNumber, textRange);
    }

    public PersonalNumber getPersonalNumber() {
        return getValue();
    }

}
