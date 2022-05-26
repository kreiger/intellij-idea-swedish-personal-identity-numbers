package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.util.ProcessingContext;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.FileRange;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumber;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormats;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static java.lang.Boolean.TRUE;

public class PersonaldentityNumberReferenceContributor extends PsiReferenceContributor {

    private static final @NotNull Key<Boolean> KEY = Key.create(PersonalIdentityNumbersInspection.class.getSimpleName());

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement().with(new PatternCondition<>("PersonalNumberLeaf") {
            @Override
            public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
                PersonalIdentityNumbersInspection inspection = PersonalIdentityNumbersInspection.getInstance(element);
                PsiElement firstChild = element.getFirstChild();
                PersonalIdentityNumberFormats formats = inspection.getFormats();
                if (firstChild == null) {
                    return formats.ranges(element.getText()).findFirst().isPresent();
                }
                for (PsiElement child = firstChild; child != null ; child = child.getNextSibling()) {
                    if (child.getFirstChild() != null) return false;
                    if (hasReference(child)) return false;
                    if (formats.ranges(child.getText()).findFirst().isPresent()) {
                        return true;
                    }
                }
                return false;
            }
        }), new PsiReferenceProvider() {
            @Override
            public PsiReference [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                PersonalIdentityNumbersInspection inspection = PersonalIdentityNumbersInspection.getInstance(element);
                if (inspection == null) {
                    return PsiReference.EMPTY_ARRAY;
                }
                Map<String, List<FileRange>> whitelist = inspection.getWhitelist();
                PersonalIdentityNumberFormats formats = inspection.getFormats();
                return formats.ranges(element.getText())
                        .flatMap(rangedPersonalNumber -> {
                            String personalIdentityNumber = rangedPersonalNumber.getPersonalIdentityNumber().toString();
                            TextRange textRange = rangedPersonalNumber.getTextRange();
                            List<FileRange> whitelistFileRanges = whitelist.get(personalIdentityNumber);
                            if (whitelistFileRanges == null || whitelistFileRanges.isEmpty()) {
                                return Stream.empty();
                            }
                            VirtualFile containingFile = element.getContainingFile().getVirtualFile();
                            element.putUserData(KEY, TRUE);
                            return whitelistFileRanges.stream()
                                    .filter(fileRange -> !fileRange.getFile().equals(containingFile) || !textRange.equals(fileRange.getTextRange()))
                                    .flatMap(fileRange -> getPsiElement(element.getProject(), personalIdentityNumber, fileRange.getFile(), fileRange.getTextRange())
                                            .map(target -> new WhitelistReference(element, textRange, target))
                                            .stream())
                                    ;
                        })
                        .toArray(PsiReference[]::new);
            }
        });
    }

    private boolean hasReference(PsiElement element) {
        return TRUE.equals(element.getUserData(KEY));
    }

    @NotNull
    static Optional<PsiElement> getPsiElement(Project project, String personalIdentityNumber, VirtualFile file, TextRange range) {
        PsiManager psiManager = PsiManager.getInstance(project);
        return Optional.ofNullable(psiManager.findFile(file))
                .map(psiFile -> psiFile.findElementAt(range.getStartOffset()))
                .map(e -> new WhitelistedPersonalNumber(e, range, personalIdentityNumber, file.getPresentableName()));
    }


    private static class WhitelistReference extends PsiReferenceBase.Immediate<PsiElement> {
        public WhitelistReference(@NotNull PsiElement element, TextRange textRange, PsiElement target) {
            super(element, textRange, target);
        }


    }

    private static class WhitelistedPersonalNumber extends FakePsiElement {
        private final PsiElement parent;
        private final TextRange textRange;
        private final String presentableText;
        private final String locationString;

        public WhitelistedPersonalNumber(PsiElement parent, TextRange range, String presentableText, String locationString) {
            this.parent = parent;
            this.textRange = range;
            this.presentableText = presentableText;
            this.locationString = locationString;
        }

        @Override public int getTextOffset() {
            return getTextRange().getStartOffset();
        }

        @Override public PsiElement getParent() {
            return parent;
        }

        @Override public TextRange getTextRange() {
            return textRange;
        }

        @Override public String getPresentableText() {
            return presentableText;
        }

        @Override public String getLocationString() {
            return locationString;
        }

        @Override public @Nullable @NonNls String getText() {
            return presentableText;
        }

        @Override public String getName() {
            return presentableText;
        }


        @Override public String toString() {
            return presentableText;
        }
    }
}
