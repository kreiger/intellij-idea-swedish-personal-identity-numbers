package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.notification.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.notification.NotificationType.*;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.SwedishPersonalNumbersInspection.addWhitelistFiles;
import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;

class DownloadWhitelistQuickFix implements LocalQuickFix {

    private final static String BASE_URL = "https://skatteverket.entryscape.net/store/9/resource/";

    private final static List<String> URLS = Stream.concat(
                    Stream.of("149", "535", "686", "1026", "1271", "1580"),
                    Stream.of("154", "1027", "1272", "1581")
            )
            .map(s -> BASE_URL + s)
            .collect(toList());
    private static final String FILENAME = "filename=";
    public static final String FAMILY_NAME = "Download official whitelist files from Skatteverket.se";

    public DownloadWhitelistQuickFix() {
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
        return FAMILY_NAME;
    }

    public boolean availableInBatchMode() {
        return false;
    }

    @Override public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        download(project, files -> addWhitelistFiles(descriptor.getPsiElement(), files));
    }

    public static void download(Project project, Consumer<List<VirtualFile>> filesConsumer) {
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, true,
                false);

        fileChooserDescriptor.setTitle("Select download directory for whitelist files");
        FileChooser.chooseFile(fileChooserDescriptor, project,
                null,
                dir -> {
                    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
                    List<Path> paths = download(project, dir);
                    Objects.requireNonNull(paths);
                    List<VirtualFile> files = paths.stream()
                            .map(virtualFileManager::refreshAndFindFileByNioPath)
                            .collect(toList());
                    filesConsumer.accept(files);
                });
    }

    private static List<Path> download(@NotNull Project project, VirtualFile dir) {
        return ProgressManager.getInstance().run(new Task.WithResult<List<Path>, UncheckedIOException>(project, "Downloading whitelists", true) {
            @Override public List<Path> compute(@NotNull ProgressIndicator indicator) {
                List<Path> paths = new ArrayList<>();
                List<Path> alreadyExists = new ArrayList<>();
                for (String url : URLS) {
                    try {
                        paths.add(HttpRequests.request(url)
                                .productNameAsUserAgent()
                                .connect(request -> {
                                    String contentDisposition = request.getConnection().getHeaderField("Content-Disposition");
                                    String fileName = getFileName(url, contentDisposition);
                                    Path path = Objects.requireNonNull(dir.toNioPath().resolve(fileName));
                                    if (Files.exists(path)) {
                                        alreadyExists.add(path);
                                        return path;
                                    }
                                    return Objects.requireNonNull(request.saveToFile(path, indicator));
                                }));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                NotificationGroup group = NotificationGroupManager.getInstance()
                        .getNotificationGroup("Swedish Personal Identity Numbers");
                Notification notification = alreadyExists.isEmpty()
                        ? group.createNotification("Downloaded " + paths.size() + " whitelist files from Skatteverket", INFORMATION)
                        : group.createNotification("Downloaded " + paths.size() + " whitelist files", "Existing files were not overwritten: "+ joinFileNames(alreadyExists), INFORMATION);
                notification.notify(project);

                return paths;
            }
        });
    }

    @NotNull private static String joinFileNames(List<Path> alreadyExists) {
        return alreadyExists.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(joining(", "));
    }

    private static Path ensureUniqueName(Path path) {
        Path fileName = path.getFileName();
        for (int i = 1; Files.exists(path); i++) {
            path = path.resolveSibling(fileName.toString() + "." + i);
        }
        return path;
    }

    private static String getFileName(String url, String contentDisposition) {
        String fileName = Optional.ofNullable(contentDisposition)
                .flatMap(DownloadWhitelistQuickFix::getContentDispositionFileName)
                .orElseGet(() -> substringAfterLast(url, "/") + ".csv");
        return fileName;
    }

    @NotNull private static Optional<String> getContentDispositionFileName(String contentDisposition) {
        String fileName;
        int startIdx = contentDisposition.indexOf(FILENAME);
        if (startIdx < 0) {
            return Optional.empty();
        }
        int endIdx = contentDisposition.indexOf(';', startIdx);
        fileName = contentDisposition.substring(startIdx + FILENAME.length(), endIdx > 0 ? endIdx : contentDisposition.length());
        if (StringUtil.startsWithChar(fileName, '\"') && StringUtil.endsWithChar(fileName, '\"')) {
            fileName = fileName.substring(1, fileName.length() - 1);
        }
        fileName = fileName.replaceAll(".*[/\\\\]", "");
        return Optional.of(fileName);
    }
}
