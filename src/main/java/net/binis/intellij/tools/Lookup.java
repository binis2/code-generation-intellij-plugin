package net.binis.intellij.tools;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.Builder;
import lombok.Data;
import net.binis.codegen.discovery.Discoverer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.nonNull;

public class Lookup {

    private static final Logger log = Logger.getInstance(Lookup.class);

    private static final Map<String, LookupDescription> classes = new ConcurrentHashMap<>();
    private static final Set<String> protoypes = initPrototypesList();
    private static final Set<String> nonProtoypes = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> generated = new ConcurrentHashMap<>();
    private static final Set<String> nonGenerated = Collections.synchronizedSet(new HashSet<>());

    public static final Set<String> STARTERS = Set.of("create", "with", "find");

    protected static Set<String> initPrototypesList() {
        var result = Collections.synchronizedSet(new HashSet<String>());
        result.add("net.binis.codegen.annotation.CodePrototype");
        result.add("net.binis.codegen.annotation.EnumPrototype");
        result.add("net.binis.codegen.annotation.builder.CodeBuilder");
        result.add("net.binis.codegen.spring.annotation.builder.CodeQueryBuilder");
        result.add("net.binis.codegen.annotation.builder.CodeValidationBuilder");
        result.add("net.binis.codegen.annotation.builder.CodeRequest");

        return result;
    }

    public static void registerClass(PsiClass cls) {
        if (nonNull(cls.getQualifiedName())) {
            classes.put(cls.getQualifiedName(), LookupDescription.builder()
                    .cls(cls)
                    .prototype(Arrays.stream(cls.getAnnotations()).anyMatch(Lookup::isPrototypeAnnotation))
                    .build());
            checkGenerated(cls);
        }
    }

    public static boolean isPrototype(String name) {
        if (nonNull(name)) {
            var cls = classes.get(name);
            if (nonNull(cls)) {
                return cls.isPrototype();
            } else {
                findClass(name).ifPresent(Lookup::registerClass);
                cls = classes.get(name);
                if (nonNull(cls)) {
                    return cls.isPrototype();
                }
            }
        }

        return false;
    }

    public static boolean isPrototype(PsiClass clas) {
        if (nonNull(clas) && nonNull(clas.getQualifiedName())) {
            var cls = classes.get(clas.getQualifiedName());
            if (nonNull(cls)) {
                return cls.isPrototype();
            } else {
                Lookup.registerClass(clas);
                cls = classes.get(clas.getQualifiedName());
                if (nonNull(cls)) {
                    return cls.isPrototype();
                }
            }
        }

        return false;
    }

    public static boolean isGenerated(String name) {
        if (nonNull(name)) {
            if (generated.containsKey(name)) {
                return true;
            }

            if (nonGenerated.contains(name)) {
                return false;
            }

            if (classes.containsKey(name)) {
                return false;
            }

            return findClass(name)
                    .filter(Lookup::checkGenerated)
                    .isPresent();
        }
        return false;
    }

    protected static boolean checkGenerated(PsiClass cls) {
            var ann = cls.getAnnotation("javax.annotation.processing.Generated");
            if (nonNull(ann)) {
                var value = ann.findAttributeValue("value");
                if (value instanceof PsiLiteralExpression exp) {
                    var proto = (String) exp.getValue();
                    generated.put(cls.getQualifiedName(), proto);
                    findClass(proto).ifPresent(Lookup::registerClass);
                    return true;
                }
            }
        nonGenerated.add(cls.getQualifiedName());
        return false;
    }

    public static String getPrototype(String name) {
        return generated.get(name);
    }

    public static PsiClass getPrototypeClass(String name) {
        var proto = generated.get(name);
        if (nonNull(proto)) {
            var cls = classes.get(proto);
            if (nonNull(cls)) {
                return cls.cls;
            }
        }

        return null;
    }


    public static Optional<PsiClass> findClass(String name) {
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            for (var c : JavaPsiFacade.getInstance(project).findClasses(name, GlobalSearchScope.allScope(project))) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static PsiIdentifier findIdentifier(PsiClass cls) {
        return Arrays.stream(cls.getChildren())
                .filter(PsiIdentifier.class::isInstance)
                .map(PsiIdentifier.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static boolean isPrototypeAnnotation(PsiAnnotation proto) {
        if (protoypes.contains(proto.getQualifiedName())) {
            return true;
        }

        if (nonProtoypes.contains(proto.getQualifiedName())) {
            return false;
        }

        return discoverAnnotation(proto);
    }

    private static boolean discoverAnnotation(PsiAnnotation proto) {
        var cls = Lookup.findClass(proto.getQualifiedName());
        if (cls.isPresent()) {
            var parent = cls.get().getParent();
            while (nonNull(parent.getParent())) {
                parent = parent.getParent();
            }

            if (parent instanceof PsiDirectory dir && dir.getName().endsWith(".jar")) {
                var resourceDir = Arrays.stream(dir.getChildren())
                        .filter(PsiDirectory.class::isInstance)
                        .map(PsiDirectory.class::cast)
                        .filter(c -> "binis".equals(c.getName()))
                        .findFirst();
                if (resourceDir.isPresent()) {
                    var file = Arrays.stream(resourceDir.get().getChildren())
                            .filter(PsiFile.class::isInstance)
                            .map(PsiFile.class::cast)
                            .filter(c -> "annotations".equals(c.getName()))
                            .findFirst();

                    if (file.isPresent() && file.get() instanceof PsiPlainTextFile text) {
                        Discoverer.findAnnotations(text.getText()).stream().filter(d -> d.getType().equals(Discoverer.TEMPLATE)).forEach(d -> protoypes.add(d.getName()));
                        if (protoypes.contains(proto.getQualifiedName())) {
                            return true;
                        }
                    }
                }
            }
        }

        nonProtoypes.add(proto.getQualifiedName());

        return false;
    }

    public static String getGeneratedName(String name) {
        if (name.endsWith("Prototype")) {
            name = name.substring(0, name.length() - 9);
            if (name.endsWith("Entity")) {
                name = name.substring(0, name.length() - 6);
            }
        }
        return name.replace(".prototype.", ".");
    }

    public static PsiMethodCallExpression findRoot(PsiMethodCallExpression expression) {
        if (STARTERS.contains(expression.getMethodExpression().getReferenceName()) && expression.getArgumentList().isEmpty()) {
            return expression;
        }

        var lambda = PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression.class);
        if (nonNull(lambda)) {
            var mtd = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
            if (nonNull(mtd)) {
                expression = mtd;
            }
        }

        var refs = PsiTreeUtil.getChildrenOfType(expression, PsiReferenceExpression.class);
        if (nonNull(refs)) {
            for (var ref : refs) {
                var mtds = PsiTreeUtil.getChildrenOfType(ref, PsiMethodCallExpression.class);
                if (nonNull(mtds)) {
                    for (var mtd : mtds) {
                        var result = findRoot(mtd);
                        if (nonNull(result)) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Builder
    @Data
    public static class LookupDescription {
        private boolean prototype;
        private PsiClass cls;
    }

}
