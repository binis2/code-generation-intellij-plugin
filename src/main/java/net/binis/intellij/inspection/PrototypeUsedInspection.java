package net.binis.intellij.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.annotation.type.GenerationStrategy;
import net.binis.intellij.tools.Binis;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.nonNull;

public class PrototypeUsedInspection extends AbstractBaseJavaLocalInspectionTool {

    private final QuickFix myQuickFix = new QuickFix();

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                Lookup.registerClass(aClass);
            }

            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiJavaCodeReferenceElement ref && shouldProcess(element)) {
                    checkForError(element, ref.getQualifiedName(), holder);
                }
                super.visitElement(element);
            }
        };
    }

    protected boolean shouldProcess(PsiElement element) {
        if (Binis.isCodeGenUsed(element)) {
            var cls = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (nonNull(cls) && nonNull(cls.getQualifiedName())) {
                return !Lookup.isPrototype(cls.getQualifiedName());
            }
        }

        return false;
    }

    protected void checkForError(PsiElement element, String cls, ProblemsHolder holder) {
        if (!(element.getParent() instanceof PsiAnnotation)) {
            var data = Lookup.getPrototypeData(cls);
            if (nonNull(data) && GenerationStrategy.PROTOTYPE.equals(data.getStrategy())) {
                holder.registerProblem(element,
                        InspectionBundle.message("inspection.binis.codegen.problem.descriptor"),
                        myQuickFix);
            }
        }
    }

    @Override
    public @Nullable @Nls String getStaticDescription() {
        return "Checks if the prototype is used instead of the generated object!.";
    }

    private static class QuickFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return InspectionBundle.message("inspection.binis.codegen.use.quickfix");
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            var element = descriptor.getPsiElement();
            var factory = JavaPsiFacade.getInstance(project).getElementFactory();

            if (element instanceof PsiJavaCodeReferenceElement type) {
                var name = type.getQualifiedName();
                Lookup.findClass(Lookup.getGeneratedName(name)).ifPresent(c ->
                    type.replace(factory.createClassReferenceElement(c)));
            }
        }

        @NotNull
        public String getFamilyName() {
            return getName();
        }

    }

}
