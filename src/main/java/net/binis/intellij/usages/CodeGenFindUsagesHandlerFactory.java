package net.binis.intellij.usages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import net.binis.codegen.generation.core.Helpers;
import net.binis.intellij.tools.Binis;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

import static java.util.Objects.nonNull;
import static net.binis.codegen.tools.Tools.nullCheck;
import static net.binis.codegen.tools.Tools.with;

public class CodeGenFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        try {
            if (!DumbService.isDumb(element.getProject())) {
                if (element instanceof PsiClass cls && (Lookup.isPrototype(cls) || Lookup.isGenerated(cls))) {
                    return true;
                }

                return element instanceof PsiMethod field && field.getParent() instanceof PsiClass cls && cls.isInterface() && (Lookup.isPrototype(cls) || Lookup.isGenerated(cls));
            }
        } catch (IndexNotReadyException e) {
            //Ignore
        }
        return false;
    }

    @Override
    public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        return new FindUsagesHandler(element) {
            @Override
            public PsiElement @NotNull [] getPrimaryElements() {
                if (Binis.isCodeGenUsed(element)) {
                    if (element instanceof PsiClass cls && nonNull(cls.getQualifiedName())) {
                        var name = Lookup.getGeneratedName(cls.getQualifiedName());
                        var generated = Lookup.findClass(name);
                        if (generated.isPresent()) {
                            var result = new ArrayList<PsiElement>();
                            if (Lookup.isGenerated(cls)) {
                                nullCheck(Lookup.getPrototypeClass(name), result::add);
                            }
                            result.add(generated.get());
                            return result.toArray(PsiElement[]::new);
                        }
                    } else if (element instanceof PsiMethod method && method.getParent() instanceof PsiClass cls && nonNull(cls.getQualifiedName())) {
                        if (Lookup.isPrototype(cls)) {
                            var name = Lookup.getGeneratedName(cls.getQualifiedName());
                            var generated = Lookup.findClass(name);
                            if (generated.isPresent()) {
                                var result = new ArrayList<PsiElement>();
                                Arrays.stream(generated.get().getAllMethods())
                                        .filter(m ->
                                                method.getName().equals(m.getName()) ||
                                                        (nonNull(m.getReturnType()) && m.getName().equals(Helpers.getGetterName(method.getName(), m.getReturnType().getPresentableText()))))
                                        .forEach(result::add);
                                Arrays.stream(generated.get().getAllMethods()).filter(m -> "with".equals(m.getName())).findFirst().ifPresent(m ->
                                        with(m.getReturnType(), type ->
                                                Lookup.findClass(type.getCanonicalText()).ifPresent(c ->
                                                        Arrays.stream(c.getAllMethods()).filter(w ->
                                                                method.getName().equals(w.getName())).forEach(result::add))));
                                if (!result.isEmpty()) {
                                    return result.toArray(PsiElement[]::new);
                                }
                            }
                        } else {
                            var proto = Lookup.getPrototypeClass(cls);
                            if (nonNull(proto)) {
                                var result = new ArrayList<PsiElement>();
                                var name = method.getName().startsWith("get") || method.getName().startsWith("is") ? Helpers.getFieldName(method.getName()) : method.getName();
                                Arrays.stream(proto.getAllMethods())
                                        .filter(m -> name.equals(m.getName()))
                                        .forEach(result::add);
                                if (!result.isEmpty()) {
                                    return result.toArray(PsiElement[]::new);
                                }
                            }
                        }
                    }
                }

                return PsiElement.EMPTY_ARRAY;
            }
        };
    }

}
