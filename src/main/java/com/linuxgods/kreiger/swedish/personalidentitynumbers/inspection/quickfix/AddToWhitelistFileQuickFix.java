package com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumber;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection.WHITELIST_FORMATS;
import static one.util.streamex.MoreCollectors.tail;

public class AddToWhitelistFileQuickFix implements LocalQuickFix {

    private final PersonalIdentityNumbersInspection inspection;
    private final VirtualFile virtualFile;
    private final PersonalIdentityNumber personalIdentityNumber;

    public AddToWhitelistFileQuickFix(PersonalIdentityNumbersInspection inspection, VirtualFile virtualFile, PersonalIdentityNumber personalIdentityNumber) {
        this.inspection = inspection;
        this.virtualFile = virtualFile;
        this.personalIdentityNumber = personalIdentityNumber;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Add to whitelist file '" + virtualFile.getPresentableName() + "'";
    }

    @Override
    public @IntentionName @NotNull String getName() {
        return "Add '" + personalIdentityNumber + "' to whitelist file '" + virtualFile.getPresentableName() + "'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        CommandProcessor.getInstance().executeCommand(project, () -> {
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            Document document = fileDocumentManager.getDocument(virtualFile);
            if (document == null) return;
            CharSequence charsSequence = document.getCharsSequence();
            List<PersonalIdentityNumberRange> tail = WHITELIST_FORMATS.ranges(charsSequence)
                    .collect(tail(3));
            if (tail.size() == 3) {
                String betwixt = charsSequence.subSequence(tail.get(0).getTextRange().getEndOffset(),
                        tail.get(1).getTextRange().getStartOffset()).toString();
                if (betwixt.equals(charsSequence.subSequence(tail.get(1).getTextRange().getEndOffset(),
                        tail.get(2).getTextRange().getStartOffset()).toString())) {
                    String toAppend = betwixt + personalIdentityNumber;
                    document.insertString(tail.get(2).getTextRange().getEndOffset(), toAppend);
                    inspection.init();
                    return;
                }
            }
            int length = charsSequence.length();
            String toAppend;
            if (length > 0 && charsSequence.charAt(length - 1) != '\n') {
                toAppend = "\n" + personalIdentityNumber;
            } else {
                toAppend = personalIdentityNumber + "\n";
            }
            document.insertString(length, toAppend);
            inspection.init();
        }, "", null);
    }
}
