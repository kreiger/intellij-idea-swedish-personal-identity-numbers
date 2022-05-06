package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.tree.FileRenderer;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Consumer;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.PersonalNumberFormat.formatWithCentury;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.Requirement.ALLOWED;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.Requirement.REJECTED;
import static com.linuxgods.kreiger.swedish.personalidentitynumbers.SwedishPersonalNumbersInspection.defaultFormats;

class SwedishPersonalNumbersInspectionOptionsPanel extends InspectionOptionsPanel {
    public static final String SKATTEVERKET_URL = "https://skatteverket.se/omoss/apierochoppnadata/kunskapochinspiration/alltdubehovervetaomtestpersonnummer.4.5b35a6251761e6914202df9.html";

    SwedishPersonalNumbersInspectionOptionsPanel(SwedishPersonalNumbersInspection inspection) {
        PersonalNumberFormats formats = inspection.getFormats();
        JBTabbedPane tabs = new JBTabbedPane(SwingConstants.TOP);
        tabs.add("Personal number formats", getFormatsPanel(inspection, formats));
        tabs.add("Whitelist files", getWhitelistPanel(inspection));
        add(tabs, "span, wrap, grow");
    }

    @NotNull
    private JPanel getFormatsPanel(SwedishPersonalNumbersInspection inspection, PersonalNumberFormats formats) {
        CollectionListModel<PersonalNumberFormat> listModel = new CollectionListModel<>(formats.getFormats(), true);
        AnActionButton resetButton = new AnActionButton("Reset", AllIcons.General.Reset) {
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
        JBList<PersonalNumberFormat> list = new JBList<>(listModel);
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list);
        toolbarDecorator.setPreferredSize(getMinimumListSize());
        toolbarDecorator.setAddAction(anActionButton -> {
            PersonalNumberFormat patternBuilder = formatWithCentury(ALLOWED);
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
        panel.add(checkBox("Allow coordination numbers (\"samordningsnummer\")", formats.getCoordinationNumber(),
                formats::setCoordinationNumber), BorderLayout.SOUTH);
        return panel;
    }

    private void edit(CollectionListModel<PersonalNumberFormat> listModel, JBList<PersonalNumberFormat> list) {
        PersonalNumberFormat copy = list.getSelectedValue().copy();
        if (new PersonalNumberFormatDialog(copy).showAndGet()) {
            listModel.setElementAt(copy, list.getSelectedIndex());
        }
    }

    @NotNull
    private JPanel getWhitelistPanel(SwedishPersonalNumbersInspection inspection) {
        Set<VirtualFile> whitelistFiles = inspection.getWhitelistFiles();
        CollectionListModel<VirtualFile> model = new CollectionListModel<>(whitelistFiles);
        model.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                inspection.setWhitelistUrls(SwedishPersonalNumbersInspection.getFileUrls(model.getItems()));
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                inspection.setWhitelistUrls(SwedishPersonalNumbersInspection.getFileUrls(model.getItems()));
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                inspection.setWhitelistUrls(SwedishPersonalNumbersInspection.getFileUrls(model.getItems()));
            }
        });

        JBList<VirtualFile> list = new JBList<>(model);
        list.setCellRenderer(new FileRenderer().forList());
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list, model);
        toolbarDecorator.disableUpDownActions();
        toolbarDecorator.setAddAction(button -> {
            FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, true,
                    true);

            FileChooser.chooseFiles(fileChooserDescriptor, CommonDataKeys.PROJECT.getData(button.getDataContext()), null, model::add);

        });
        toolbarDecorator.setMinimumSize(InspectionOptionsPanel.getMinimumListSize());
        AnActionButton downloadButton = new AnActionButton(DownloadWhitelistQuickFix.FAMILY_NAME, AllIcons.ToolbarDecorator.AddLink) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                DownloadWhitelistQuickFix.download(e.getProject(), model::add);
            }
        };
        toolbarDecorator.addExtraAction(downloadButton);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbarDecorator.createPanel());
        panel.add(getLinkLabel(), BorderLayout.SOUTH);
        return panel;
    }

    private BrowserLink getLinkLabel() {
        return new BrowserLink(null, "Find official CSV files at skatteverket.se", SKATTEVERKET_URL,
                SKATTEVERKET_URL);
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

    private class PersonalNumberFormatDialog extends DialogWrapper {

        private final JPanel centerPanel;

        public PersonalNumberFormatDialog(PersonalNumberFormat patternBuilder) {
            super(SwedishPersonalNumbersInspectionOptionsPanel.this, false);
            centerPanel = new PersonalNumberFormatPanel(patternBuilder);
            setTitle("Personal Number Format");
            init();
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            return centerPanel;
        }

        private class PersonalNumberFormatPanel extends InspectionOptionsPanel {
            public PersonalNumberFormatPanel(PersonalNumberFormat pb) {

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
                add(copyLink, "align right, wrap");

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

        private void updateLabel(PersonalNumberFormat pb, JLabel formatLabel) {
            formatLabel.setText(pb.formatString());
        }
    }
}
