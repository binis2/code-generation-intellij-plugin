package net.binis.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static java.util.Objects.nonNull;

public class CodeGenReferenceContributor extends PsiReferenceContributor {

    private static final Logger log = Logger.getInstance(CodeGenReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        try {
                            if (!DumbService.isDumb(element.getProject())) {
                                var ann = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
                                if (nonNull(ann) && element instanceof PsiLiteralExpression expression) {
                                    if (element.getParent() instanceof PsiNameValuePair pair &&
                                            "value".equals(pair.getName()) &&
                                            pair.getParent() instanceof PsiAnnotationParameterList list &&
                                            "javax.annotation.processing.Generated".equals(ann.getQualifiedName())) {
                                        var value = (String) expression.getValue();
                                        return buildClassReferences(element, value);
                                    } else if ("net.binis.codegen.annotation.Default".equals(ann.getQualifiedName())) {
                                        var value = (String) expression.getValue();
                                        return buildClassReferences(element, value);
                                    }
                                }
                            }
                        } catch (ProcessCanceledException e) {
                            throw e;
                        } catch (Exception e) {
                            log.warn(e);
                        }
                        return PsiReference.EMPTY_ARRAY;
                    }
                });
    }

    protected PsiReference[] buildClassReferences(PsiElement element, String value) {
        var result = new ArrayList<PsiReference>();
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            for (var cls : JavaPsiFacade.getInstance(project).findClasses(value, GlobalSearchScope.allScope(project))) {
                result.add(new CodeGenReference(element, new TextRange(1, value.length() + 1), cls));
            }
        }
        return result.toArray(new PsiReference[0]);
    }

}
