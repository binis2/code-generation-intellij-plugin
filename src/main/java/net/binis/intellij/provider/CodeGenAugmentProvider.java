package net.binis.intellij.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NullUtils.notNull;

public class CodeGenAugmentProvider extends PsiAugmentProvider {

    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                          @NotNull final Class<Psi> type) {
        return getAugments(element, type, null);
    }

    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                          @NotNull final Class<Psi> type,
                                                          @Nullable String nameHint) {
        var result = Collections.<PsiMethod>emptyList();
        if (element instanceof PsiClass cls && cls.getLanguage().isKindOf(JavaLanguage.INSTANCE) && cls.isAnnotationType() && notNull(cls.getExtendsList())) {
            var methods = new ArrayList<PsiMethod>();
            Arrays.stream(cls.getExtendsList().getReferencedTypes())
                    .map(PsiClassType::resolve)
                    .filter(Objects::nonNull)
                    .forEach(t ->
                            Arrays.stream(t.getAllMethods())
                                    .filter(PsiAnnotationMethodImpl.class::isInstance)
                                    .filter(m -> methods.stream().noneMatch(a ->
                                            a.getName().equals(m.getName())))
                                    .map(PsiElement::copy)
                                    .forEach(m -> methods.add((PsiMethod) m)));
            result = methods;
        }

        return (List) result;
    }
}
