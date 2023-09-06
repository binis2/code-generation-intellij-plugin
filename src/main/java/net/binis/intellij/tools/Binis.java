package net.binis.intellij.tools;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import lombok.Builder;
import lombok.Getter;
import net.binis.intellij.util.CodeGenDependenciesUtil;

import java.util.HashMap;
import java.util.Map;

public class Binis {

    protected static final Map<Project, ProjectInfo> projects = new HashMap<>();

    public static boolean isCodeGenUsed(PsiElement element) {
        return getProjectInfo(element).isCodeGenUsed();
    }

    public static boolean isPluginEnabled(Project project) {
        return getProjectInfo(project).isCodeGenUsed(); //TODO: Add logic to check for plugin enabled.
    }

    public static boolean isAnnotationInheritanceEnabled(Project project) {
        return isPluginEnabled(project);
    }

    public static boolean isBracketlessMethodsEnabled(Project project) {
        return false;
    }

    protected static ProjectInfo getProjectInfo(PsiElement element) {
        return getProjectInfo(element.getProject());
    }

    protected static ProjectInfo getProjectInfo(Project project) {
        return projects.computeIfAbsent(project, p -> ProjectInfo.builder()
                .codeGenUsed(CodeGenDependenciesUtil.isUsingCodeGenJars(p))
                .pluginEnabled(true)
                .build());
    }

    public static void clear(Project project) {
        projects.remove(project);
    }


    @Builder
    @Getter
    protected static class ProjectInfo {
        protected boolean codeGenUsed;
        protected boolean pluginEnabled;
    }

}
