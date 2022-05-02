package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.linuxgods.kreiger.swedish.personalidentitynumbers.SwedishPersonalNumbersInspection.getCurrentProfile;
import static java.util.stream.Collectors.*;

public class SwedishPersonalNumberWhitelistFileListener implements BulkFileListener {

    private Project project;

    public SwedishPersonalNumberWhitelistFileListener(Project project) {
        this.project = project;

    }

    @Override public void after(@NotNull List<? extends VFileEvent> events) {
        InspectionProfileImpl currentProfile = getCurrentProfile(project);
        ToolsImpl tools = currentProfile.getToolsOrNull(SwedishPersonalNumbersInspection.SHORT_NAME, project);

        Map<VirtualFile, List<SwedishPersonalNumbersInspection>> map = getInspections(tools)
                .flatMap(i -> i.getWhitelistFiles().stream().map(virtualFile -> Map.entry(virtualFile, i)))
                .collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            List<SwedishPersonalNumbersInspection> inspections = map.get(file);
            if (inspections != null) {
                for (SwedishPersonalNumbersInspection inspection : inspections) {
                    inspection.init();
                }
            }
        }
    }

    @NotNull
    private Stream<SwedishPersonalNumbersInspection> getInspections(ToolsImpl tools) {
        return tools.getTools()
                .stream()
                .map(ScopeToolState::getTool)
                .map(InspectionToolWrapper::getTool)
                .map(SwedishPersonalNumbersInspection.class::cast);
    }
}
