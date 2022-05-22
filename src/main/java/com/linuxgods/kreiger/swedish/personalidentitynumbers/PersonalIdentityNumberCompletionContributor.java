package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.FileRange;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumber;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class PersonalIdentityNumberCompletionContributor extends CompletionContributor {

    public static final Predicate<String> NON_DIGIT = Pattern.compile("\\D").asPredicate();

    public PersonalIdentityNumberCompletionContributor() {
        extend(CompletionType.BASIC, psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
                PrefixMatcher prefixMatcher = result.getPrefixMatcher();
                String prefix = prefixMatcher.getPrefix();
                if (prefix.isEmpty() || NON_DIGIT.test(prefix)) return;
                PsiElement originalElement = parameters.getOriginalPosition();
                if (originalElement == null) return;
                PersonalIdentityNumbersInspection.getInstance(originalElement).getWhitelist()
                        .entrySet().stream()
                        .flatMap(e -> {
                            PersonalIdentityNumber personalIdentityNumber = e.getKey();
                            List<FileRange> fileRanges = e.getValue();
                            return fileRanges.stream().map(fileRange -> {
                                LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(personalIdentityNumber.toString());
                                return PersonaldentityNumberReferenceContributor.getPsiElement(originalElement.getProject(), personalIdentityNumber, fileRange.getFile(), fileRange.getTextRange())
                                        .map(lookupElementBuilder::withPsiElement)
                                        .orElse(lookupElementBuilder);
                            });
                        })
                        .forEach(result::addElement);
            }
        });
    }
}
