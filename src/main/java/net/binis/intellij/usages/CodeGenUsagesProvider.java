package net.binis.intellij.usages;

import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class CodeGenUsagesProvider extends JavaFindUsagesProvider {

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return super.canFindUsagesFor(psiElement);
    }

    @NotNull
    @Override
    public String getType(@NotNull PsiElement element) {
        return super.getType(element);
    }

    @NotNull
    @Override
    public String getDescriptiveName(@NotNull PsiElement element) {
        return super.getDescriptiveName(element);
    }

    @NotNull
    @Override
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return super.getNodeText(element, useFullName);
    }

}

