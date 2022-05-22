package com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ReplaceQuickFix implements LocalQuickFix {
    private final String replacement;
    private final TextRange textRange;
    private final String name;
    private String familyName;

    public ReplaceQuickFix(String familyName, String name, TextRange textRange, String replacement) {
        this.replacement = replacement;
        this.textRange = textRange;
        this.familyName = familyName;
        this.name = name;
    }

    @Override public @IntentionName @NotNull String getName() {
        return name;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return familyName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        TextRange textRange = element.getTextRange()
                .cutOut(descriptor.getTextRangeInElement())
                .cutOut(this.textRange)
                ;
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(element.getContainingFile());
        if (null != document && document.isWritable()) {
            document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),
                    replacement);
            documentManager.commitDocument(document);
        }
    }
}
