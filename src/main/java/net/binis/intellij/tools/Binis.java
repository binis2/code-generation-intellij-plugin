package net.binis.intellij.tools;

import com.intellij.openapi.project.Project;

public class Binis {

    public static boolean isPluginEnabled(Project project) {
        return true;
    }

    public static boolean isAnnotationInheritanceEnabled(Project project) {
        return isPluginEnabled(project);
    }

    public static boolean isBracketlessMethodsEnabled(Project project) {
        return isPluginEnabled(project);
    }



}
