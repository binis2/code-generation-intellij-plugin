package net.binis.intellij;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.generation.core.interfaces.PrototypeData;
import net.binis.intellij.tools.Lookup;
import net.binis.intellij.util.PrototypeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static net.binis.codegen.annotation.type.GenerationStrategy.*;
import static net.binis.codegen.tools.Tools.*;

public class CodeGenAnnotator implements Annotator {

    private Logger log = Logger.getInstance(CodeGenAnnotator.class);

    private static final Key<Void> GENERATED_KEY = Key.create("binis.generated");

    private static final Set<String> HIGHLIGHT_METHODS = initHighlightMethods();

    private static Set<String> initHighlightMethods() {
        var result = new HashSet<String>();

        result.add("create");
        result.add("find");
        result.add("with");
        result.add("done");
        result.add("by");
        result.add("and");
        result.add("or");
        result.add("in");
        result.add("order");
        result.add("asc");
        result.add("desc");
        result.add("where");
        result.add("merge");
        result.add("as");
        result.add("sum");
        result.add("min");
        result.add("max");
        result.add("count");
        result.add("avg");
        result.add("distinct");
        result.add("group");
        result.add("_add");
        result.add("_and");
        result.add("_if");
        result.add("_self");
        result.add("_map");
        result.add("save");
        result.add("delete");
        result.add("join");
        result.add("joinFetch");
        result.add("leftJoin");
        result.add("leftJoinFetch");
        result.add("ensure");
        result.add("reference");
        result.add("references");
        result.add("get");
        result.add("list");
        result.add("top");
        result.add("page");
        result.add("paginated");
        result.add("paged");
        result.add("tuple");
        result.add("tuples");
        result.add("prepare");
        result.add("projection");
        result.add("flush");
        result.add("lock");
        result.add("hint");
        result.add("filter");
        result.add("exists");
        result.add("notExists");
        result.add("remove");
        result.add("run");
        result.add("transaction");
        result.add("size");
        result.add("contains");
        result.add("notContains");
        result.add("containsAll");
        result.add("containsOne");
        result.add("containsNone");
        result.add("isEmpty");
        result.add("isNotEmpty");
        result.add("builder");
        result.add("build");

        return result;
    }

    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
        try {
            if (!DumbService.isDumb(element.getProject())) {
                if (element instanceof PsiClass cls) {
                    var data = Lookup.getPrototypeData(cls);
                    if (nonNull(data)) {
                        var intf = Lookup.getGeneratedName(cls);
                        var intfCls = Lookup.findClass(intf);
                        var ident = Lookup.findIdentifier(cls);
                        if (nonNull(ident)) {
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .tooltip((intfCls.map(PsiElement::getContainingFile).isPresent() ? "Generated" : "Generates") + " <a href=\"#navigation/" + intfCls.map(psiClass -> (psiClass.getContainingFile().getVirtualFile().getCanonicalPath() + ":" + psiClass.getTextOffset())).orElse("unknown") + "\">" + intf + "</a>")
                                    .range(ident.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.CLASS_NAME).create();
                        }
                    } else if (Lookup.isGenerated(cls.getQualifiedName())) {
                        var c = Lookup.getPrototypeClass(cls.getQualifiedName());
                        var ident = Lookup.findIdentifier(cls);
                        if (nonNull(ident) && nonNull(c) && nonNull(c.getContainingFile())) {
                            var path = cls.getContainingFile().getVirtualFile().getCanonicalPath();
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .tooltip("Generated by <a href=\"#navigation/" + path + ":" + c.getTextOffset() + "\">" + c.getQualifiedName() + "</a>")
                                    .range(ident.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.CLASS_NAME).create();
                        }
                    }
                } else if (element instanceof PsiJavaCodeReferenceElement ref) {
                    if (Lookup.isGenerated(ref.getQualifiedName())) {
                        var cls = Lookup.getPrototypeClass(ref.getQualifiedName());
                        if (nonNull(cls) && nonNull(cls.getContainingFile())) {
                            var path = cls.getContainingFile().getVirtualFile().getCanonicalPath();
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .tooltip("Generated by <a href=\"#navigation/" + path + ":" + cls.getTextOffset() + "\">" + cls.getQualifiedName() + "</a>")
                                    .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
                        }
                    } else {
                        var data = Lookup.getPrototypeData(ref.getQualifiedName());
                        if (nonNull(data)) {
                            var intf = Lookup.getGeneratedName(ref.getQualifiedName());
                            var intfCls = Lookup.findClass(intf);
                            if (intfCls.map(PsiElement::getContainingFile).isPresent()) {
                                if (ref.getParent() instanceof PsiAnnotation || !PROTOTYPE.equals(data.getStrategy())) {
                                    if (checkForErrors(data, element, ref, holder)) {
                                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                                .tooltip("Generation strategy: " + (Lookup.isEnum(data) ? "Enum" : data.getStrategy().name()))
                                                .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE).create();
                                    }
                                } else {
                                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                            .tooltip("Generates <a href=\"#navigation/" + intfCls.map(psiClass -> (psiClass.getContainingFile().getVirtualFile().getCanonicalPath() + ":" + psiClass.getTextOffset())).orElse("unknown") + "\">" + intf + "</a>")
                                            .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
                                }
                            }
                        } else {
                            data = Lookup.isPrototypeAnnotation(ref.getQualifiedName());
                            if (nonNull(data)) {
                                if (checkForErrors(data, element, ref, holder)) {
                                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                            .tooltip("Generation strategy: " + (Lookup.isEnum(data) ? "Enum" : data.getStrategy().name()))
                                            .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE).create();
                                }
                            } else if (nonNull(ref.getParent()) && ref.getParent() instanceof PsiAnnotation ann) {
                                var valid = Lookup.isValidationAnnotation(ann.getQualifiedName());
                                if (nonNull(valid) && valid.isValidationAnnotation()) {
                                    checkForValidationErrors(valid, ref, ann, holder);
                                }
                            } else {
                                var resolved = ref.resolve();
                                if (resolved instanceof PsiField field && field.getParent() instanceof PsiClass cls && Lookup.isGenerated(cls.getQualifiedName())) {
                                    with(Lookup.getPrototypeClass(cls.getQualifiedName()), proto ->
                                            with(Lookup.getPrototypeData(proto), dta ->
                                                    condition(Lookup.isEnum(dta), () ->
                                                            calcEnumTooltip(element, field, holder, cls, proto, dta))));
                                }
                            }
                        }
                    }
                } else if (element instanceof PsiMethodCallExpression exp) {
                    var root = Lookup.findRoot(exp);
                    if (nonNull(root)) {
                        var r = exp.getMethodExpression();
                        var name = r.getReferenceName();
                        if (HIGHLIGHT_METHODS.contains(name)) {
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .range(new TextRange(r.getTextOffset(), r.getTextOffset() + name.length())).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
                        }
                    }
                }
            }
        } catch (IndexNotReadyException e) {
            // ignore
        }
    }

    protected void calcEnumTooltip(PsiElement element, PsiField field, AnnotationHolder holder, PsiClass cls, PsiClass proto, PrototypeData dta) {
        try {
            with(PsiTreeUtil.getChildOfType(PsiTreeUtil.getContextOfType(PsiTreeUtil.getChildOfType(field.getInitializer(), PsiReferenceExpression.class), PsiMethodCallExpression.class), PsiExpressionList.class), values -> {
                var impl = Lookup.findClass(PsiTreeUtil.getChildOfType((PsiElement) cls.getAnnotation("net.binis.codegen.annotation.Default").getAttributes().get(0), PsiLiteralExpression.class).getValue().toString()).get();
                var paramNames = impl.getConstructors()[0].getParameterList();
                var expressions = values.getExpressions();

                var tooltip = new StringBuilder();

                tooltip.append("<b>Enum value:</b> <a href=\"#navigation/")
                        .append(proto.getContainingFile().getVirtualFile().getCanonicalPath()).append(":")
                        .append(Arrays.stream(proto.getFields()).filter(f -> f.getName().equals(field.getName())).findFirst().get().getTextOffset())
                        .append("\">").append(((PsiLiteralExpression) expressions[1]).getValue().toString())
                        .append("</a><br/>")
                        .append("<b>Ordinal value:</b> ")
                        .append(expressions[2].getText())
                        .append("<br/>");

                for (var i = 2; i < paramNames.getParametersCount(); i++) {
                    tooltip.append("<b>")
                            .append(paramNames.getParameters()[i].getName())
                            .append(":</b> ")
                            .append(expressions[i + 1].getText())
                            .append("<br/>");
                }

                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .tooltip(tooltip.toString())
                        .range(element.getTextRange()).create();

            });
        } catch (Exception e) {
            log.warn(e);
        }
    }

    private void checkForValidationErrors(Lookup.ValidationDescription data, PsiJavaCodeReferenceElement element, PsiAnnotation annotation, AnnotationHolder holder) {
        var cls = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (isNull(cls) || !cls.isInterface() || (!Lookup.isPrototype(cls) && !cls.isAnnotationType())) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Validation annotations are evaluated only on prototypes!")
                    .range(element.getTextRange()).create();
        }

        var method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (isNull(method)) {
            if (!cls.isAnnotationType()) {
                holder.newAnnotation(HighlightSeverity.WARNING, "Validation annotations are evaluated only on prototype methods!")
                        .range(element.getTextRange()).create();
            }
        } else {
            data = Lookup.processTargets(annotation, data);
            if (nonNull(method.getReturnType())) {
                var type = method.getReturnType().getCanonicalText();

                with(data.getTargets(), targets -> {
                    var valid = targets.isEmpty();
                    for (var target : targets) {
                        if (target.equals(type)) {
                            valid = true;
                            break;
                        }
                    }

                    if (!valid) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Target '" + type + "' is not in the list of allowed targets for '" + annotation.getNameReferenceElement().getText() + "': " + targets)
                                .range(element.getTextRange()).create();
                    }
                });
            }
        }
    }

    protected boolean checkForErrors(PrototypeData data, PsiElement element, PsiJavaCodeReferenceElement ref, AnnotationHolder holder) {

        var result = true;

        var cls = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (nonNull(cls) && !cls.isAnnotationType() && isNull(PsiTreeUtil.getParentOfType(element, PsiClassObjectAccessExpression.class))) {
            if (Lookup.isEnum(data)) {
                if (!cls.isEnum()) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "@" + ref.getText() + " is allowed only on enums!")
                            .range(element.getTextRange()).create();
                    result = false;
                }
            } else {
                var strategy = nullCheck(PrototypeUtil.readPrototypeStrategy(element), data.getStrategy());

                if (!cls.isInterface() && in(strategy, PROTOTYPE, IMPLEMENTATION, PLAIN)) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "@" + ref.getText() + " is allowed only on interfaces!")
                            .range(element.getTextRange()).create();
                    result = false;
                }
                if (in(strategy, PROTOTYPE, PLAIN)) {
                    var intf = Lookup.getGeneratedName(cls);
                    if (intf.equals(cls.getQualifiedName())) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Either rename class to '" + cls.getName() + "Prototype' or move it to a '*.prototypes.*' package!")
                                .range(element.getTextRange()).create();
                        result = false;
                    }
                }
            }
        }
        return result;
    }

}
