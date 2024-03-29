package net.binis.intellij.listeners;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.NotNull;
import net.binis.intellij.tools.Binis;

public class CodeGenManagerListener implements ProjectManagerListener {

    @Override
    public void projectClosed(@NotNull Project project) {
        Binis.clear(project);
    }

}
