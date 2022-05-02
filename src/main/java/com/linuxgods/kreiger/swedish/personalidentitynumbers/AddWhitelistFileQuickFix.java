package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.SwedishPersonalNumbersInspection.addWhitelistFiles;

class AddWhitelistFileQuickFix implements LocalQuickFix {

    public AddWhitelistFileQuickFix() {
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
        return "Add whitelist file(s)";
    }

    public boolean availableInBatchMode() {
        return false;
    }

    @Override public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, true,
                true);

        FileChooser.chooseFiles(fileChooserDescriptor, project,
                null,
                virtualFiles -> {
                    addWhitelistFiles(descriptor.getPsiElement(), virtualFiles);
                });
    }
}
