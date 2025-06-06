package com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.IconUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.ui.MessageType.*;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection.WHITELISTS_ORDER;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection.addWhitelistFiles;
import static java.util.Collections.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

public class DownloadWhitelistQuickFix implements LocalQuickFix {

    private final static String BASE_URL = "https://skatteverket.entryscape.net/store/9/resource/";
    private final static String PERSONAL_IDENTITY_NUMBERS_CATALOG_URL = BASE_URL+"150";
    private final static String COORDINATION_NUMBERS_CATALOG_URL = BASE_URL+"155";

    private static final String FILENAME = "filename=";
    public static final String FAMILY_NAME = "Download official whitelist files from Skatteverket.se";
    public static final @NotNull Icon NOTIFICATION_ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", DownloadWhitelistQuickFix.class);

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
                                                  .sorted(WHITELISTS_ORDER)
                                                  .collect(toList());
                    filesConsumer.accept(files);
                });
    }

    private static List<Path> download(@NotNull Project project, VirtualFile dir) {
        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup("Swedish Personal Identity Numbers");
        return ProgressManager.getInstance().run(new Task.WithResult<List<Path>, UncheckedIOException>(project, "Downloading whitelists", true) {
            @Override public List<Path> compute(@NotNull ProgressIndicator indicator) {
                List<Path> paths = new ArrayList<>();
                List<Path> alreadyExists = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                urls.addAll(getUrlsFromCatalogRdf(PERSONAL_IDENTITY_NUMBERS_CATALOG_URL));
                urls.addAll(getUrlsFromCatalogRdf(COORDINATION_NUMBERS_CATALOG_URL));

                List<String> failedUrls = new ArrayList<>();
                for (String url : urls) {
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
                                    path = Objects.requireNonNull(request.saveToFile(path, indicator));
                                    path.toFile().setWritable(false, false);
                                    return path;
                                }));
                    } catch (IOException e) {
                        failedUrls.add(url);
                    }
                }

                if (!failedUrls.isEmpty()) {
                    Notification failure = group.createNotification("Failed to download "+failedUrls.size()+" whitelist files", WARNING);
                    failure.setIcon(AllIcons.General.Warning);
                    failure.notify(project);
                }
                Notification notification = alreadyExists.isEmpty()
                        ? group.createNotification("Downloaded " + paths.size() + " whitelist files from Skatteverket", INFORMATION)
                        : group.createNotification("Downloaded " + paths.size() + " whitelist files", "Existing files were not overwritten: " + joinFileNames(alreadyExists), INFORMATION);
                notification.setImportant(true);
                VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
                Collection<AnAction> selectInProjectViewActions = paths.stream()
                                                                      .map(virtualFileManager::refreshAndFindFileByNioPath)
                                                                      .filter(Objects::nonNull)
                                                                      .sorted(WHITELISTS_ORDER)
                                                                      .map(file -> createSelectInProjectViewAction(file, project))
                                                                      .collect(toList());

                notification.setIcon(NOTIFICATION_ICON);
                notification.addActions(selectInProjectViewActions);
                notification.setDropDownText("Whitelist files...");
                notification.notify(project);

                return paths;
            }

            private @NotNull List<String> getUrlsFromCatalogRdf(String catalogUrl) {
                try {
                    return HttpRequests.request(catalogUrl)
                        .productNameAsUserAgent()
                        .connect(request -> {
                            List<String> urls = new ArrayList<>();
                            Document document = parseXml(request.getInputStream());
                            NodeList downloadURL = document.getElementsByTagNameNS("http://www.w3.org/ns/dcat#", "downloadURL");
                            if (downloadURL.getLength() == 0) {
                                throw new RuntimeException("Failed to parse catalog RDF");
                            }
                            for (int i = 0; i < downloadURL.getLength(); i++) {
                                NamedNodeMap attributes = downloadURL.item(i).getAttributes();
                                Node resource = attributes.getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
                                if (resource != null) {
                                    urls.add(resource.getNodeValue());
                                }
                            }
                            if (urls.isEmpty()) {
                                throw new RuntimeException("Failed to parse catalog RDF");
                            }
                            return urls;
                        });
                } catch (IOException e) {
                    Notification failure = group.createNotification("Failed to download whitelist catalog from "+ catalogUrl, WARNING);
                    failure.setIcon(AllIcons.General.Warning);
                    failure.notify(project);
                    return emptyList();
                }
            }
        });
    }

    private static Document parseXml(@NotNull InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder documentBuilder = factory
                    .newDocumentBuilder();
            return documentBuilder
                    .parse(inputStream);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @NotNull
    private static AnAction createSelectInProjectViewAction(VirtualFile file, @NotNull Project project) {
        AnAction action = new AnAction() {
            @Override public void actionPerformed(@NotNull AnActionEvent e) {
                SelectInTarget target = SelectInManager.findSelectInTarget(ToolWindowId.PROJECT_VIEW, project);
                if (target != null) target.selectIn(new FileSelectInContext(project, file), false);
                OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project, file);
                if (openFileDescriptor.canNavigate()) {
                    openFileDescriptor.navigate(true);
                }
            }
        };
        Presentation presentation = action.getTemplatePresentation();
        presentation.setText(file.getPresentableName(), false);
        presentation.setIcon(IconUtil.getIcon(file, 0, project));
        return action;
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
