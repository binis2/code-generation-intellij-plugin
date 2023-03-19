package net.binis.intellij.usages;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.NotNull;

/**
 * Provides implicit usages of lombok fields
 */
public class CodeGenImplicitUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return isUsage(element);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return isUsage(element);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return isUsage(element);
    }

    protected boolean isUsage(PsiElement element) {
        if (element instanceof PsiClass cls) {
            return Lookup.isPrototype(cls);
        }

        return Lookup.isPrototype(PsiTreeUtil.getParentOfType(element, PsiClass.class));

    }

}
