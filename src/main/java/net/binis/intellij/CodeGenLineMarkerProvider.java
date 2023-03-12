package net.binis.intellij;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.task.ProjectTaskManager;
import net.binis.intellij.icons.CodeGenIcons;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.Collection;

import static java.util.Objects.nonNull;

public class CodeGenLineMarkerProvider extends RelatedItemLineMarkerProvider {

    private Logger log = Logger.getInstance(CodeGenLineMarkerProvider.class);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiClass cls && Lookup.isPrototype(cls)) {
            var ident = Lookup.findIdentifier(cls);
            NavigationGutterIconBuilder<PsiElement> builder =
                    NavigationGutterIconBuilder.create(CodeGenIcons.GENERATE)
                            .setAlignment(GutterIconRenderer.Alignment.RIGHT)
                            .setTargets(nonNull(ident) ? ident : element.getParent())
                            .setTooltipText("Generate files");
            result.add(builder.createLineMarkerInfo(element, IconGutterHandler.INSTANCE));
        }
    }

    private static class IconGutterHandler implements GutterIconNavigationHandler<PsiElement> {
        static final IconGutterHandler INSTANCE = new IconGutterHandler();

        private Logger log = Logger.getInstance(IconGutterHandler.class);

        @Override
        public void navigate(MouseEvent e, PsiElement element) {
            var module = ProjectFileIndex.getInstance(element.getProject()).getModuleForFile(element.getContainingFile().getVirtualFile());
            if (nonNull(module)) {
                ProjectTaskManager.getInstance(element.getProject()).build(module);
            }
        }
    }

}
