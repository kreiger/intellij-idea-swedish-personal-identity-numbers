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
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.ReplaceQuickFix;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.*;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormat.formatWithCentury;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.Requirement.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

public class PersonalIdentityNumbersInspection extends LocalInspectionTool {

    public static final @NotNull String SHORT_NAME = "SwedishPersonalIdentityNumbers"; // Matches plugin.xml
    public static final PersonalIdentityNumberFormats WHITELIST_FORMATS = new PersonalIdentityNumberFormats(
            List.of(formatWithCentury(ALLOWED).setInvalidChecksumAllowed(true)));

    private Set<String> whitelistUrls = new LinkedHashSet<>();
    private Set<VirtualFile> whitelistFiles = null;
    private PatriciaTrie<List<FileRange>> whitelist = null;

    private PersonalIdentityNumberFormats formats = new PersonalIdentityNumberFormats(defaultFormats());

    @NotNull
    static List<PersonalIdentityNumberFormat> defaultFormats() {
        return List.of(
                formatWithCentury(REQUIRED).setInvalidChecksumAllowed(true),
                formatWithCentury(REJECTED).setSeparator(REQUIRED).setInvalidChecksumAllowed(true)
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

    private PatriciaTrie<List<FileRange>> initWhitelist(Set<VirtualFile> whitelistFiles) {
        Map<VirtualFile, List<PersonalIdentityNumberPatternMatch>> filesPersonalNumbers = getFilesPersonalNumbers(whitelistFiles);
        this.whitelistFiles = whitelistFiles;
        PatriciaTrie<List<FileRange>> whitelist = initWhitelist(filesPersonalNumbers);
        this.whitelist = whitelist;
        return whitelist;
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        Set<VirtualFile> whitelistFiles = getWhitelistFiles();
        PatriciaTrie<List<FileRange>> whitelist = getWhitelist();
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);
                if (element.getFirstChild() != null) return;
                formats.ranges(element.getText()).forEach(rpn -> {
                    TextRange textRange = rpn.getTextRange();
                    registerProblems(element, textRange, rpn, whitelist, whitelistFiles, holder, isOnTheFly);
                });
            }

            @Override
            public void visitPlainText(@NotNull PsiPlainText content) {
                formats.ranges(content.getText())
                        .forEach(
                                pnr -> registerProblems(content, pnr.getTextRange(), pnr, whitelist,
                                        whitelistFiles,
                                        holder, isOnTheFly));
            }
        };
    }

    private void registerProblems(PsiElement element, TextRange textRange, PersonalIdentityNumberPatternMatch match, PatriciaTrie<List<FileRange>> whitelist, Set<VirtualFile> whitelistFiles, ProblemsHolder holder, boolean isOnTheFly) {
        PersonalIdentityNumber personalIdentityNumber = match.getPersonalIdentityNumber();
        if (isOnTheFly) {
            match.getFixes().forEach(fix -> {
                String numberType = getNumberType(match);
                holder.registerProblem(element, "Swedish " + numberType + " " + fix, INFORMATION, textRange,
                        fix.getQuickFixes());
            });
        }
        List<FileRange> fileRanges = whitelist.get(personalIdentityNumber.toString());
        if (fileRanges == null) {
            LocalQuickFix[] quickFixes = getQuickFixes(match, textRange.getLength(), whitelist, whitelistFiles).toArray(LocalQuickFix[]::new);
            String description = getDescription(match, whitelistFiles);
            holder.registerProblem(element, textRange, description, quickFixes);
        }
    }

    @NotNull private String getNumberType(PersonalIdentityNumberPatternMatch match) {
        return (match.isCoordinationNumber()
                ? "coordination number"
                : "personal identity number") + " '" + match.getPersonalIdentityNumber() + "'";
    }

    @NotNull private String getDescription(PersonalIdentityNumberPatternMatch match, Set<VirtualFile> whitelistFiles) {
        String numberType = getNumberType(match);
        if (whitelistFiles.isEmpty()) {
            return "Swedish " + numberType + " not in whitelist, no whitelist files configured";
        }
        if (whitelistFiles.size() == 1) {
            return "Swedish " + numberType + " not in whitelist file '" + whitelistFiles.iterator().next().getPresentableName() + "'";
        }
        long writableCount = whitelistFiles.stream().filter(VirtualFile::isWritable).count();
        if (writableCount == 0) {
            return "Swedish " + numberType + " not in the official whitelist files";
        }
        if (writableCount == whitelistFiles.size()) {
            return "Swedish " + numberType + " not in the " + writableCount + " custom whitelist files";
        }
        return "Swedish " + numberType + " not in official or custom whitelist files";
    }

    Set<VirtualFile> getWhitelistFiles(Collection<String> whitelistUrls) {
        VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
        return whitelistUrls.stream()
                .map(virtualFileManager::refreshAndFindFileByUrl)
                .filter(Objects::nonNull)
                .sorted(comparing(VirtualFile::isWritable).reversed()
                        .thenComparing(file -> file.getParent().getPath())
                        .thenComparing(VirtualFile::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(toCollection(LinkedHashSet::new));
    }

    @NotNull
    public Stream<LocalQuickFix> getQuickFixes(PersonalIdentityNumberPatternMatch match, int length, PatriciaTrie<List<FileRange>> whitelist, Set<VirtualFile> whitelistFiles) {
        PersonalIdentityNumber personalIdentityNumber = match.getPersonalIdentityNumber();
        List<LocalQuickFix> fixes = new ArrayList<>();
        getLower(whitelist, personalIdentityNumber)
                .ifPresent(lower -> fixes.add(replaceFix(length, lower.formatLike(match))));
        getHigher(whitelist, personalIdentityNumber)
                .ifPresent(higher -> fixes.add(replaceFix(length, higher.formatLike(match))));
        getWritable(whitelistFiles)
                .map(virtualFile -> new AddToWhitelistFileQuickFix(this, virtualFile, personalIdentityNumber))
                .forEach(fixes::add);
        fixes.add(new AddWhitelistFileQuickFix());
        boolean hasReadOnlyFiles = whitelistFiles.stream()
                .anyMatch(virtualFile -> !virtualFile.isWritable());
        if (!hasReadOnlyFiles) {
            // Files are downloaded as read only,
            // so only present the option if we don't have read only files.
            fixes.add(new DownloadWhitelistQuickFix());
        }
        return fixes.stream();
    }

    @NotNull private Stream<VirtualFile> getWritable(Set<VirtualFile> whitelistFiles) {
        return whitelistFiles.stream()
                .filter(VirtualFile::isWritable);
    }

    private Optional<PersonalIdentityNumber> getHigher(PatriciaTrie<List<FileRange>> whitelist, PersonalIdentityNumber personalIdentityNumber) {
        SortedMap<String, List<FileRange>> tailMap = whitelist.tailMap(personalIdentityNumber.toString());
        if (tailMap.isEmpty()) return Optional.empty();
        String higher = tailMap.firstKey();
        while (higher != null && PersonalIdentityNumber.isCoordinationNumber(higher) != personalIdentityNumber.isCoordinationNumber()) {
            higher = whitelist.nextKey(higher);
        }
        return Optional.ofNullable(higher).map(PersonalIdentityNumber::new);
    }

    private Optional<PersonalIdentityNumber> getLower(PatriciaTrie<List<FileRange>> whitelist, PersonalIdentityNumber personalIdentityNumber) {
        SortedMap<String, List<FileRange>> headMap = whitelist.headMap(personalIdentityNumber.toString());
        if (headMap.isEmpty()) return Optional.empty();
        String lower = headMap.lastKey();
        while (lower != null && PersonalIdentityNumber.isCoordinationNumber(lower) != personalIdentityNumber.isCoordinationNumber()) {
            lower = whitelist.previousKey(lower);
        }
        return Optional.ofNullable(lower).map(PersonalIdentityNumber::new);
    }

    @NotNull private ReplaceQuickFix replaceFix(int length, String replacement) {
        return new ReplaceQuickFix(
                "Replace with whitelisted",
                "Replace with whitelisted '" + replacement + "'",
                TextRange.from(0, length), replacement);
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

    private PatriciaTrie<List<FileRange>> initWhitelist(Map<VirtualFile, List<PersonalIdentityNumberPatternMatch>> whitelistPersonalNumbersByFile) {
        return whitelistPersonalNumbersByFile.entrySet().stream().flatMap(filePnrs -> {
            VirtualFile file = filePnrs.getKey();
            List<PersonalIdentityNumberPatternMatch> pnrs = filePnrs.getValue();
            return pnrs.stream()
                    .map(pnr -> Map.entry(pnr.getPersonalIdentityNumber().toString(), new FileRange(file, pnr.getTextRange())));
        }).collect(groupingBy(Map.Entry::getKey, PatriciaTrie::new, mapping(Map.Entry::getValue, toList())));
    }

    private Map<VirtualFile, List<PersonalIdentityNumberPatternMatch>> getFilesPersonalNumbers(Collection<VirtualFile> whitelistFiles) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        return whitelistFiles.stream().flatMap(virtualFile -> {
            Document document = fileDocumentManager.getDocument(virtualFile);
            if (document == null) return Stream.empty();
            document.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) {
                    document.removeDocumentListener(this);
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

    public PatriciaTrie<List<FileRange>> getWhitelist() {
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
