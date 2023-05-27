package net.binis.intellij;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import net.binis.codegen.annotation.type.GenerationStrategy;
import net.binis.intellij.tools.Lookup;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.nonNull;

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
            if (element instanceof PsiClass cls) {
                if (Lookup.isPrototype(cls)) {
                    var intf = Lookup.getGeneratedName(cls);
                    var intfCls = Lookup.findClass(intf);
                    var ident = Lookup.findIdentifier(cls);
                    if (nonNull(ident)) {
                        holder.newAnnotation(HighlightSeverity.INFORMATION, "test")
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
                            if (ref.getParent() instanceof PsiAnnotation || !GenerationStrategy.PROTOTYPE.equals(data.getStrategy())) {
                                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                        .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
                            } else {
                                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                        .tooltip("Generates <a href=\"#navigation/" + intfCls.map(psiClass -> (psiClass.getContainingFile().getVirtualFile().getCanonicalPath() + ":" + psiClass.getTextOffset())).orElse("unknown") + "\">" + intf + "</a>")
                                        .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.KEYWORD).create();
                            }
                        }
                    } else {
                        data = Lookup.isPrototypeAnnotation(ref.getQualifiedName());
                        if (nonNull(data)) {
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .tooltip("Generation strategy: " + data.getStrategy().name())
                                    .range(element.getTextRange()).textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE).create();
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
        } catch (IndexNotReadyException e) {
            // ignore
        }
    }

}
