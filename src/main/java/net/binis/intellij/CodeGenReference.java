package net.binis.intellij;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.nonNull;

public class CodeGenReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final PsiClass cls;

    public CodeGenReference(@NotNull PsiElement element, TextRange textRange, PsiClass cls) {
        super(element, textRange);
        this.cls = cls;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        if (nonNull(cls)) {
            results.add(new PsiElementResolveResult(cls));
        }
        return results.toArray(new ResolveResult[results.size()]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<LookupElement> variants = new ArrayList<>();
        return variants.toArray();
    }

}