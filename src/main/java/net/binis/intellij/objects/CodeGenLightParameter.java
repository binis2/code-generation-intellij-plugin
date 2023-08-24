package net.binis.intellij.objects;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightParameter;
import lombok.Getter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@Getter
public class CodeGenLightParameter extends LightParameter {

    private final PsiModifierList orgModifierList;
    private final boolean initializer;

    public CodeGenLightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, PsiModifierList modifierList, boolean initializer) {
        super(name, type, declarationScope);
        this.orgModifierList = modifierList;
        this.initializer = initializer;
    }

}
