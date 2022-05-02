package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.SwedishPersonalNumbersInspection.WHITELIST_FORMATS;
import static one.util.streamex.MoreCollectors.tail;

public class AddToWhitelistFileQuickFix implements LocalQuickFix {

    private final SwedishPersonalNumbersInspection inspection;
    private final VirtualFile virtualFile;
    private final PersonalNumber personalNumber;

    public AddToWhitelistFileQuickFix(SwedishPersonalNumbersInspection inspection, VirtualFile virtualFile, PersonalNumber personalNumber) {
        this.inspection = inspection;
        this.virtualFile = virtualFile;
        this.personalNumber = personalNumber;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Add to whitelist file '" + virtualFile.getPresentableName() + "'";
    }

    @Override
    public @IntentionName @NotNull String getName() {
        return "Add '" + personalNumber + "' to whitelist file '" + virtualFile.getPresentableName() + "'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        CommandProcessor.getInstance().executeCommand(project, () -> {
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            Document document = fileDocumentManager.getDocument(virtualFile);
            if (document == null) return;
            CharSequence charsSequence = document.getCharsSequence();
            List<PersonalNumberRange> tail = WHITELIST_FORMATS.ranges(charsSequence)
                    .collect(tail(3));
            if (tail.size() == 3) {
                String betwixt = charsSequence.subSequence(tail.get(0).getTextRange().getEndOffset(),
                        tail.get(1).getTextRange().getStartOffset()).toString();
                if (betwixt.equals(charsSequence.subSequence(tail.get(1).getTextRange().getEndOffset(),
                        tail.get(2).getTextRange().getStartOffset()).toString())) {
                    String toAppend = betwixt + personalNumber;
                    document.insertString(tail.get(2).getTextRange().getEndOffset(), toAppend);
                    inspection.init();
                    return;
                }
            }
            int length = charsSequence.length();
            String toAppend;
            if (length > 0 && charsSequence.charAt(length - 1) != '\n') {
                toAppend = "\n" + personalNumber;
            } else {
                toAppend = personalNumber + "\n";
            }
            document.insertString(length, toAppend);
            inspection.init();
        }, "", null);
    }
}
