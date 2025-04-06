package com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection;

import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import com.intellij.util.IconUtil;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.quickfix.DownloadWhitelistQuickFix;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormat;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormats;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.model.Requirement;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.icons.AllIcons.Ide.External_link_arrow;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection.defaultFormats;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.PersonalIdentityNumberFormat.formatWithCentury;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.Requirement.ALLOWED;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.model.Requirement.REJECTED;

class PersonalIdentityNumbersInspectionOptionsPanel extends JPanel {
    public static final String SKATTEVERKET_URL = "https://skatteverket.se/omoss/apierochoppnadata/kunskapochinspiration/alltdubehovervetaomtestpersonnummer.4.5b35a6251761e6914202df9.html";
    public static final String COORDINATION_NUMBERS_URL = "https://www.skatteverket.se/servicelankar/otherlanguages/inenglish/individualsandemployees/coordinationnumbers.4.1657ce2817f5a993c3a7d2a.html";

    PersonalIdentityNumbersInspectionOptionsPanel(PersonalIdentityNumbersInspection inspection) {
        super(new BorderLayout());
        PersonalIdentityNumberFormats formats = inspection.getFormats();

        JBTabbedPane tabs = new JBTabbedPane(SwingConstants.TOP);

        JPanel formatsPanel = getFormatsPanel(tabs, inspection);
        tabs.add("Personal number formats", formatsPanel);

        JPanel whitelistPanel = getWhitelistPanel(inspection);
        tabs.add("Whitelist files", whitelistPanel);

        add(tabs);
    }

    @NotNull
    private JPanel getFormatsPanel(JBTabbedPane tabs, PersonalIdentityNumbersInspection inspection) {
        PersonalIdentityNumberFormats formats = inspection.getFormats();
        CollectionListModel<PersonalIdentityNumberFormat> listModel = new CollectionListModel<>(formats.getFormats(), true);
        AnActionButton resetButton = new AnActionButton("Reset to default", "Reset to default", AllIcons.General.Reset) {
            @Override public void updateButton(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(!defaultFormats().equals(formats.getFormats()));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                listModel.removeAll();
                listModel.addAll(0, defaultFormats());
            }
        };
        listModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                inspection.setFormats(formats);
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                inspection.setFormats(formats);
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                inspection.setFormats(formats);
            }
        });
        JBList<PersonalIdentityNumberFormat> list = new JBList<>(listModel);
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list);
        toolbarDecorator.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
        toolbarDecorator.setAddAction(anActionButton -> {
            PersonalIdentityNumberFormat patternBuilder = formatWithCentury(ALLOWED);
            if (new PersonalNumberFormatDialog(patternBuilder).showAndGet()) {
                listModel.add(patternBuilder);
            }
        });
        toolbarDecorator.setEditAction(anActionButton -> {
            edit(listModel, list);
        });
        new DoubleClickListener() {
            @Override protected boolean onDoubleClick(@NotNull MouseEvent event) {
                edit(listModel, list);
                return true;
            }
        }.installOn(list);
        toolbarDecorator.addExtraAction(resetButton);
        toolbarDecorator.setRemoveActionUpdater(e -> list.getModel().getSize() > 1);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbarDecorator.createPanel());
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxPanel.add(checkBox("Allow coordination numbers", formats.getCoordinationNumber(),
                formats::setCoordinationNumber));
        checkboxPanel.add(new BrowserLink(External_link_arrow, "\"samordningsnummer\"",
                COORDINATION_NUMBERS_URL, COORDINATION_NUMBERS_URL));
        panel.add(checkboxPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void edit(CollectionListModel<PersonalIdentityNumberFormat> listModel, JBList<PersonalIdentityNumberFormat> list) {
        PersonalIdentityNumberFormat copy = list.getSelectedValue().copy();
        if (new PersonalNumberFormatDialog(copy).showAndGet()) {
            listModel.setElementAt(copy, list.getSelectedIndex());
        }
    }

    @NotNull
    private JPanel getWhitelistPanel(PersonalIdentityNumbersInspection inspection) {
        Set<VirtualFile> whitelistFiles = inspection.getWhitelistFiles();
        CollectionListModel<VirtualFile> model = new CollectionListModel<>(whitelistFiles);
        model.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                inspection.setWhitelistUrls(PersonalIdentityNumbersInspection.getFileUrls(model.getItems()));
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                inspection.setWhitelistUrls(PersonalIdentityNumbersInspection.getFileUrls(model.getItems()));
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                inspection.setWhitelistUrls(PersonalIdentityNumbersInspection.getFileUrls(model.getItems()));
            }
        });

        JBList<VirtualFile> list = new JBList<>(model);
        list.setCellRenderer(new FileRelativeToProjectDirRenderer());
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list, model);
        toolbarDecorator.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
        toolbarDecorator.disableUpDownActions();
        toolbarDecorator.setAddAction(button -> {
            FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, true,
                    true);

            FileChooser.chooseFiles(fileChooserDescriptor, CommonDataKeys.PROJECT.getData(button.getDataContext()), null, model::add);

        });
        AnActionButton downloadButton = new AnActionButton(DownloadWhitelistQuickFix.FAMILY_NAME, DownloadWhitelistQuickFix.FAMILY_NAME, AllIcons.ToolbarDecorator.AddLink) {
            @Override public void updateButton(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(model.getItems().stream()
                        .allMatch(VirtualFile::isWritable));
            }

            @Override public boolean isDumbAware() {
                return true;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                DownloadWhitelistQuickFix.download(e.getProject(), model::add);
            }
        };
        toolbarDecorator.addExtraAction(downloadButton);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbarDecorator.createPanel());

        BrowserLink browserLink = new BrowserLink(External_link_arrow, "Find official CSV files at skatteverket.se", SKATTEVERKET_URL,
                SKATTEVERKET_URL);
        browserLink.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(browserLink, BorderLayout.SOUTH);
        return panel;
    }

    @NotNull
    private JBCheckBox checkBox(String label, boolean selected, Consumer<Boolean> onChange) {
        JBCheckBox cb = new JBCheckBox(label, selected);
        cb.addItemListener(e -> onChange.accept(cb.isSelected()));
        return cb;
    }

    @NotNull
    private JComboBox<String> comboBox(Requirement selected, Consumer<Requirement> setter) {
        JComboBox<String> cb = requirementComboBox();
        cb.setSelectedIndex(selected.ordinal());
        cb.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) return;
            setter.accept(Requirement.values()[cb.getSelectedIndex()]);
        });
        return cb;
    }

    @NotNull
    private JComboBox<String> requirementComboBox() {
        return new ComboBox<>(new String[]{"Required", "Allowed", "Rejected"});
    }

    private static class FileRelativeToProjectDirRenderer extends ColoredListCellRenderer<VirtualFile> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends VirtualFile> list, VirtualFile file, int index, boolean selected, boolean hasFocus) {
            Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(list));
            if (project != null) {
                setIcon(IconUtil.getIcon(file, 0, project));
            }
            SimpleTextAttributes attributes = file.isWritable()
                    ? SimpleTextAttributes.REGULAR_ATTRIBUTES
                    : SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES;
            append(file.getName(), attributes, true);
            String parentPath = getPathRelativeToProjectDir(file, project).orElseGet(() -> file.getParent().getPath());
            append("  ");
            append(parentPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }

        private Optional<String> getPathRelativeToProjectDir(VirtualFile file, Project project) {
            return Optional.ofNullable(project)
                    .map(p -> ProjectFileIndex.getInstance(project).getContentRootForFile(file, false))
                    .map(projectDir -> VfsUtil.getRelativePath(file.getParent(), projectDir));
        }

    }

    private class PersonalNumberFormatDialog extends DialogWrapper {

        private final JPanel centerPanel;

        public PersonalNumberFormatDialog(PersonalIdentityNumberFormat patternBuilder) {
            super(PersonalIdentityNumbersInspectionOptionsPanel.this, false);
            centerPanel = new PersonalNumberFormatPanel(patternBuilder);
            setTitle("Personal Number Format");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            return centerPanel;
        }

        private class PersonalNumberFormatPanel extends JPanel {
            public PersonalNumberFormatPanel(PersonalIdentityNumberFormat pb) {
                super(new MigLayout("fillx, ins 0"));
                JLabel formatLabel = new JLabel(pb.formatString());
                add(formatLabel, "gapy 5, align center, span 3");

                ActionLink copyLink = new ActionLink("Copy regex", e -> {
                    CopyPasteManager.getInstance().setContents(new StringSelection(pb.buildString()));
                    Balloon balloon = JBPopupFactory.getInstance()
                            .createBalloonBuilder(new JLabel("Copied regular expression to clipboard"))
                            .setFadeoutTime(3000)
                            .createBalloon();
                    balloon.showInCenterOf(formatLabel);
                });
                add(copyLink, "wrap");

                add(new JSeparator(), "gapy 5, growx, span, wrap");

                var millennium = requirementComboBox();
                millennium.setSelectedIndex(pb.getMillennium().ordinal());
                millennium.setEnabled(pb.getCentury() != REJECTED);
                var century = comboBox(pb.getCentury(), c -> {
                    pb.setCentury(c);
                    millennium.setEnabled(c != REJECTED);
                    updateLabel(pb, formatLabel);
                });
                millennium.addItemListener(e -> {
                    if (e.getStateChange() != ItemEvent.SELECTED) return;
                    Requirement mr = Requirement.values()[millennium.getSelectedIndex()];
                    pb.setMillennium(mr);
                    updateLabel(pb, formatLabel);
                });

                add("Century digits", "gapy 5, growx", century);
                add("Millennium digit", "growx, wrap", millennium);
                add("Separator [-+]", "growx, wrap", comboBox(pb.getSeparator(), separator -> {
                    pb.setSeparator(separator);
                    updateLabel(pb, formatLabel);
                }));

                JBCheckBox invalidChecksumCB = checkBox("Allow invalid checksum", pb.isInvalidChecksumAllowed(), s -> {
                    pb.setInvalidChecksumAllowed(s);
                    updateLabel(pb, formatLabel);
                });
                invalidChecksumCB.setEnabled(pb.getChecksumDigit() != REJECTED);
                add("Checksum digit", "growx", comboBox(pb.getChecksumDigit(), checksumDigit -> {
                    pb.setChecksumDigit(checksumDigit);
                    invalidChecksumCB.setEnabled(checksumDigit != REJECTED);
                    updateLabel(pb, formatLabel);
                }));
                add(invalidChecksumCB, "span 2, growx, wrap");

                JBCheckBox surroundingDigitsCB = checkBox("Allow surrounding digits ",
                        pb.isSurroundingDigitsAllowed(), s -> {
                            pb.setSurroundingDigitsAllowed(s);
                            updateLabel(pb, formatLabel);
                        });

                add(surroundingDigitsCB, "span 2, growx, wrap");
            }


            private void add(String label, String constraints, Component component) {
                final JLabel l = new JLabel(label);
                l.setLabelFor(component);
                add(l, "");
                add(component, constraints);
            }

        }

        private void updateLabel(PersonalIdentityNumberFormat pb, JLabel formatLabel) {
            formatLabel.setText(pb.formatString());
        }
    }
}
