package com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.AddToWhitelistFileQuickFix;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.AddWhitelistFileQuickFix;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.DownloadWhitelistQuickFix;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormat.formatWithCentury;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.Requirement.ALLOWED;
import static java.util.stream.Collectors.*;

public class PersonalIdentityNumbersInspection extends LocalInspectionTool {

    public static final @NotNull String SHORT_NAME = "SwedishPersonalIdentityNumbers"; // Matches plugin.xml
    public static final PersonalIdentityNumberFormats WHITELIST_FORMATS = new PersonalIdentityNumberFormats(
            List.of(formatWithCentury(ALLOWED).setInvalidChecksumAllowed(true)));

    private Set<String> whitelistUrls = new LinkedHashSet<>();
    private Set<VirtualFile> whitelistFiles = null;
    private Map<PersonalIdentityNumber, List<FileRange>> whitelist = null;

    private PersonalIdentityNumberFormats formats = new PersonalIdentityNumberFormats(defaultFormats());

    @NotNull
    static List<PersonalIdentityNumberFormat> defaultFormats() {
        return List.of(
                formatWithCentury(ALLOWED).setInvalidChecksumAllowed(true)
        );
    }

    public static PersonalIdentityNumbersInspection getInstance(@NotNull PsiElement element) {
        InspectionProfileImpl currentProfile = getCurrentProfile(element.getProject());
        return (PersonalIdentityNumbersInspection) currentProfile.getUnwrappedTool(SHORT_NAME, element);
    }

    @NotNull
    public static InspectionProfileImpl getCurrentProfile(Project project) {
        InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance(project);
        return inspectionProfileManager.getCurrentProfile();
    }

    public void init() {
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        whitelistUrls.removeIf(url -> null == virtualFileManager.refreshAndFindFileByUrl(url));
        whitelistFiles = null;
        whitelist = null;
    }

    private Map<PersonalIdentityNumber, List<FileRange>> initWhitelist(Set<VirtualFile> whitelistFiles) {
        Map<VirtualFile, List<PersonalIdentityNumberRange>> filesPersonalNumbers = getFilesPersonalNumbers(whitelistFiles);
        this.whitelistFiles = whitelistFiles;
        Map<PersonalIdentityNumber, List<FileRange>> whitelist = initWhitelist(filesPersonalNumbers);
        this.whitelist = whitelist;
        return whitelist;
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Set<VirtualFile> whitelistFiles = getWhitelistFiles();
        Map<PersonalIdentityNumber, List<FileRange>> whitelist = getWhitelist();
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);
                if (element.getFirstChild() != null) return;
                formats.ranges(element.getText()).forEach(rpn -> {
                    TextRange textRange = rpn.getTextRange();
                    PersonalIdentityNumber personalIdentityNumber = rpn.getValue();
                    registerProblems(element, textRange, personalIdentityNumber, whitelist, whitelistFiles, holder, isOnTheFly);
                });
            }

            @Override
            public void visitPlainText(@NotNull PsiPlainText content) {
                formats.ranges(content.getText())
                        .forEach(
                                pnr -> registerProblems(content, pnr.getTextRange(), pnr.getPersonalNumber(), whitelist,
                                        whitelistFiles,
                                        holder, isOnTheFly));
            }
        };
    }

    private void registerProblems(PsiElement element, TextRange textRange, PersonalIdentityNumber personalIdentityNumber, Map<PersonalIdentityNumber, List<FileRange>> whitelist, Set<VirtualFile> whitelistFiles, ProblemsHolder holder, boolean isOnTheFly) {
        if (isOnTheFly) {
            personalIdentityNumber.getValidationErrors().forEach(validationError -> {
                holder.registerProblem(element, "Swedish personal number " + validationError, INFORMATION, textRange,
                        validationError.getQuickFixes());
            });
        }
        List<FileRange> fileRanges = whitelist.get(personalIdentityNumber);
        if (fileRanges == null) {
            LocalQuickFix[] quickFixes = getQuickFixes(personalIdentityNumber, whitelistFiles).toArray(LocalQuickFix[]::new);
            holder.registerProblem(element, textRange, "Swedish personal number not in whitelist", quickFixes);
        }
    }

    Set<VirtualFile> getWhitelistFiles(Collection<String> whitelistUrls) {
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        return whitelistUrls.stream()
                .map(virtualFileManager::refreshAndFindFileByUrl)
                .filter(Objects::nonNull)
                .collect(toCollection(LinkedHashSet::new));
    }

    @NotNull
    public Stream<LocalQuickFix> getQuickFixes(PersonalIdentityNumber personalIdentityNumber, Set<VirtualFile> whitelistFiles) {
        List<LocalQuickFix> fixes = this.whitelistFiles.stream()
                .map(virtualFile -> new AddToWhitelistFileQuickFix(this, virtualFile, personalIdentityNumber))
                .collect(toList());
        fixes.add(new AddWhitelistFileQuickFix());
        fixes.add(new DownloadWhitelistQuickFix());
        return fixes.stream();
    }

    @Override
    public @Nullable JComponent createOptionsPanel() {
        return new PersonalIdentityNumbersInspectionOptionsPanel(this);
    }


    @NotNull
    static Set<String> getFileUrls(List<VirtualFile> items) {
        return items.stream()
                .map(VirtualFile::getUrl)
                .collect(toCollection(LinkedHashSet::new));
    }

    private Map<PersonalIdentityNumber, List<FileRange>> initWhitelist(Map<VirtualFile, List<PersonalIdentityNumberRange>> whitelistPersonalNumbersByFile) {
        return whitelistPersonalNumbersByFile.entrySet().stream().flatMap(filePnrs -> {
            VirtualFile file = filePnrs.getKey();
            List<PersonalIdentityNumberRange> pnrs = filePnrs.getValue();
            return pnrs.stream()
                    .map(pnr -> Map.entry(pnr.getPersonalNumber(), new FileRange(file, pnr.getTextRange())));
        }).collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
    }

    private Map<VirtualFile, List<PersonalIdentityNumberRange>> getFilesPersonalNumbers(Collection<VirtualFile> whitelistFiles) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        return whitelistFiles.stream().flatMap(virtualFile -> {
            Document document = fileDocumentManager.getDocument(virtualFile);
            if (document == null) return Stream.empty();
            document.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    init();
                }
            });
            CharSequence charsSequence = document.getCharsSequence();
            return WHITELIST_FORMATS.ranges(charsSequence)
                    .map(pnr -> Map.entry(virtualFile, pnr));
        }).collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
    }


    public Collection<String> getWhitelistUrls() {
        return whitelistUrls;
    }

    public void setWhitelistUrls(Collection<String> whitelistUrls) {
        this.whitelistUrls = new LinkedHashSet<>(whitelistUrls);
        init();
    }

    public Map<PersonalIdentityNumber, List<FileRange>> getWhitelist() {
        if (null == whitelist) {
            whitelist = initWhitelist(getWhitelistFiles());
        }
        return whitelist;
    }

    public Set<VirtualFile> getWhitelistFiles() {
        if (null == whitelistFiles) {
            whitelistFiles = getWhitelistFiles(whitelistUrls);
        }
        return whitelistFiles;
    }

    @Override
    public void writeSettings(@NotNull Element node) {
        super.writeSettings(node);
    }

    @Override
    public void readSettings(@NotNull Element node) {
        super.readSettings(node);
        init();
    }

    public static void addWhitelistFiles(@NotNull PsiElement element, List<VirtualFile> virtualFiles) {
        getCurrentProfile(element.getProject()).modifyProfile(model -> {
            PersonalIdentityNumbersInspection inspection = (PersonalIdentityNumbersInspection) model.getUnwrappedTool(
                    SHORT_NAME, element);
            inspection.whitelistUrls.addAll(getFileUrls(virtualFiles));
            inspection.init();
        });
    }

    public PersonalIdentityNumberFormats getFormats() {
        return formats;
    }

    public void setFormats(PersonalIdentityNumberFormats formats) {
        this.formats = formats;
        formats.setFormats(formats.getFormats());
    }

    public boolean getCoordinationNumber() {
        return formats.getCoordinationNumber();
    }

    public void setCoordinationNumber(boolean coordinationNumber) {
        formats.setCoordinationNumber(coordinationNumber);
    }

    public void addWhitelistUrl(String url) {
        whitelistUrls.add(url);
    }
}
