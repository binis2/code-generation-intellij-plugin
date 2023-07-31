package net.binis.intellij.filter;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.intellij.tools.Binis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class CodeGenHighlightErrorFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (nonNull(highlightInfo.getDescription()) && file instanceof PsiJavaFile java && Binis.isPluginEnabled((java.getProject()))) {
            var cls = PsiTreeUtil.findChildOfType(java, PsiClass.class);
            return isNull(cls) || !cls.isAnnotationType() || !"@interface may not have extends list".equals(highlightInfo.getDescription());
        }
        return true;
    }
}
