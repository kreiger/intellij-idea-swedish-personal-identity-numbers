package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.openapi.util.TextRange;

public class PersonalIdentityNumberRange extends TextRanged<PersonalIdentityNumber> {
    public PersonalIdentityNumberRange(PersonalIdentityNumber personalIdentityNumber, TextRange textRange) {
        super(personalIdentityNumber, textRange);
    }

    public PersonalIdentityNumber getPersonalNumber() {
        return getValue();
    }

}
