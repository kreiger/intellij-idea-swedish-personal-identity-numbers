package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.inspection.PersonalIdentityNumbersInspection.getCurrentProfile;
import static java.util.stream.Collectors.*;

public class PersonalIdentityNumberWhitelistFileListener implements BulkFileListener {

    private final Project project;

    public PersonalIdentityNumberWhitelistFileListener(Project project) {
        this.project = project;

    }

    @Override public void after(@NotNull List<? extends VFileEvent> events) {
        InspectionProfileImpl currentProfile = getCurrentProfile(project);
        ToolsImpl tools = currentProfile.getToolsOrNull(PersonalIdentityNumbersInspection.SHORT_NAME, project);

        Map<VirtualFile, List<PersonalIdentityNumbersInspection>> map = getInspections(tools)
                .flatMap(i -> i.getWhitelistFiles().stream().map(virtualFile -> Map.entry(virtualFile, i)))
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file == null) continue;
            List<PersonalIdentityNumbersInspection> inspections = map.get(file);
            if (inspections != null) {
                for (PersonalIdentityNumbersInspection inspection : inspections) {
                    if (event instanceof VFilePropertyChangeEvent || event instanceof VFileMoveEvent) {
                        inspection.addWhitelistUrl(file.getUrl());
                    }
                    inspection.init();
                }
            }
        }
    }

    @NotNull
    private Stream<PersonalIdentityNumbersInspection> getInspections(ToolsImpl tools) {
        return tools.getTools()
                .stream()
                .map(ScopeToolState::getTool)
                .map(InspectionToolWrapper::getTool)
                .map(PersonalIdentityNumbersInspection.class::cast);
    }
}
