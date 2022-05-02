package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class SwedishPersonalNumberCompletionContributor extends CompletionContributor {

    public static final Predicate<String> NON_DIGIT = Pattern.compile("\\D").asPredicate();

    public SwedishPersonalNumberCompletionContributor() {
        extend(CompletionType.BASIC, psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
                PrefixMatcher prefixMatcher = result.getPrefixMatcher();
                String prefix = prefixMatcher.getPrefix();
                if (prefix.isEmpty() || NON_DIGIT.test(prefix)) return;
                PsiElement originalElement = parameters.getOriginalPosition();
                if (originalElement == null) return;
                SwedishPersonalNumbersInspection.getInstance(originalElement).getWhitelist()
                        .entrySet().stream()
                        .flatMap(e -> {
                            PersonalNumber personalNumber = e.getKey();
                            List<FileRange> fileRanges = e.getValue();
                            return fileRanges.stream().map(fileRange -> {
                                LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(personalNumber.toString());
                                return SwedishPersonalNumberReferenceContributor.getPsiElement(originalElement.getProject(), personalNumber, fileRange.getFile(), fileRange.getTextRange())
                                        .map(lookupElementBuilder::withPsiElement)
                                        .orElse(lookupElementBuilder);
                            });
                        })
                        .forEach(result::addElement);
            }
        });
    }
}
