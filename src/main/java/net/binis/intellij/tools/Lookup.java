package net.binis.intellij.tools;

import com.github.javaparser.ast.expr.Name;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.Builder;
import lombok.Data;
import net.binis.codegen.annotation.CodePrototypeTemplate;
import net.binis.codegen.annotation.type.GenerationStrategy;
import net.binis.codegen.discovery.Discoverer;
import net.binis.codegen.generation.core.interfaces.PrototypeData;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.nonNull;
import static net.binis.codegen.generation.core.Structures.*;

public class Lookup {

    private static final Logger log = Logger.getInstance(Lookup.class);

    private static final Map<String, LookupDescription> classes = new ConcurrentHashMap<>();
    private static final Map<String, PrototypeData> protoypes = new ConcurrentHashMap<>();
    private static final Set<String> nonTemplates = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> processing = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> generated = new ConcurrentHashMap<>();
    private static final Set<String> nonGenerated = Collections.synchronizedSet(new HashSet<>());

    public static final Set<String> STARTERS = Set.of("create", "with", "find", "builder");

    public static void registerClass(PsiClass cls) {
        if (nonNull(cls.getQualifiedName())) {
            classes.put(cls.getQualifiedName(), LookupDescription.builder()
                    .cls(cls)
                    .prototype(Arrays.stream(cls.getAnnotations())
                            .map(a ->
                                    Optional.ofNullable(Lookup.isPrototypeAnnotation(a)))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst()
                            .orElse(null))
                    .build());
            checkGenerated(cls);
        }
    }

    public static PrototypeData getPrototypeData(String name) {
        if (nonNull(name)) {
            var cls = classes.get(name);
            if (nonNull(cls)) {
                return cls.getPrototype();
            } else {
                findClass(name).ifPresent(Lookup::registerClass);
                cls = classes.get(name);
                if (nonNull(cls)) {
                    return cls.getPrototype();
                }
            }
        }

        return null;
    }

    public static boolean isPrototype(String name) {
        return nonNull(getPrototypeData(name));
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

    public static boolean isGenerated(PsiClass cls) {
        if (nonNull(cls) && nonNull(cls.getQualifiedName())) {
            if (cls.getParent() instanceof PsiClass parent && nonNull(parent.getQualifiedName())) {
                if (isGenerated(parent)) {
                    return true;
                }
            }
            return isGenerated(cls.getQualifiedName());
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

    public static PsiClass getPrototypeClass(PsiClass cls) {
        if (nonNull(cls.getQualifiedName())) {
            if (cls.getParent() instanceof PsiClass parent) {
                var result = getPrototypeClass(parent);
                if (nonNull(result)) {
                    return result;
                }
            }
            return getPrototypeClass(cls.getQualifiedName());
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

    public static PrototypeData isPrototypeAnnotation(PsiAnnotation proto) {
        if (nonNull(proto.getQualifiedName())) {
            var result = protoypes.get(proto.getQualifiedName());

            if (nonNull(result)) {
                return result;
            }

            return discoverAnnotation(proto.getQualifiedName());
        }
        return null;
    }

    protected static PrototypeData discoverAnnotation(String name) {
        if (!nonTemplates.contains(name) && !processing.contains(name)) {
            try {
                processing.add(name);
                var cls = Lookup.findClass(name);
                if (cls.isPresent()) {
                    var parent = cls.get().getParent();
                    while (nonNull(parent.getParent())) {
                        parent = parent.getParent();
                    }

                    if (parent instanceof PsiDirectory dir) {
                        if (dir.getName().endsWith(".jar")) {
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
                                    Discoverer.findAnnotations(text.getText()).stream()
                                            .filter(d -> d.getType().equals(Discoverer.TEMPLATE))
                                            .filter(d -> !protoypes.containsKey(d.getName()))
                                            .forEach(service ->
                                                    Lookup.processPrototype(service.getName()));
                                    var result = protoypes.get(name);
                                    if (nonNull(result)) {
                                        return result;
                                    }
                                }
                            }
                        } else {
                            if (Lookup.processPrototype(name)) {
                                var result = protoypes.get(name);
                                if (nonNull(result)) {
                                    return result;
                                }
                            } else {
                                return null;
                            }
                        }
                    }
                }

                nonTemplates.add(name);
            } finally {
                processing.remove(name);
            }
        }

        return null;
    }

    protected static boolean processPrototype(String name) {
        var clas = findClass(name);
        if (clas.isPresent()) {
            if ("net.binis.codegen.annotation.CodePrototype".equals(name) || "net.binis.codegen.annotation.EnumPrototype".equals(name)) {
                return nonNull(protoypes.computeIfAbsent(name, k -> {
                    registerTemplate(clas.get());
                    return defaultProperties.get(k).get().build();
                }));
            } else {
                if (nonNull(clas.get().getAnnotation(CodePrototypeTemplate.class.getCanonicalName()))) {
                    Arrays.stream(clas.get().getAnnotations())
                            .filter(a -> !defaultProperties.containsKey(a.getQualifiedName()))
                            .filter(a -> !nonTemplates.contains(a.getQualifiedName()))
                            .forEach(a -> discoverAnnotation(a.getQualifiedName()));
                    registerTemplate(clas.get());
                    protoypes.put(name, defaultProperties.get(name).get().build());
                } else {
                    nonTemplates.add(name);
                }
                return true;
            }
        }

        return false;
    }

    public static String getGeneratedName(String name) {
        //TODO: Take annotation overrides into account
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

    protected static void readAnnotation(PsiAnnotation ann, PrototypeDataHandler.PrototypeDataHandlerBuilder builder) {
        ann.getAttributes().forEach((node) -> {
            if (node instanceof PsiNameValuePair pair) {
                switch (pair.getAttributeName()) {
                    case "name" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            var value = (String) exp.getValue();
                            if (StringUtils.isNotBlank(value)) {
                                var intf = value.replace("Entity", "");
                                builder.name(value).className(value).interfaceName(intf).longModifierName(intf + ".Modify");
                            }
                        }
                    }
                    case "generateConstructor" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.generateConstructor((Boolean) exp.getValue());
                        }
                    }
                    case "generateImplementation" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.generateImplementation((Boolean) exp.getValue());
                        }
                    }
                    case "generateInterface" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.generateInterface((Boolean) exp.getValue());
                        }
                    }
                    case "interfaceName" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.name((String) exp.getValue());
                        }
                    }
                    case "classGetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.classGetters((Boolean) exp.getValue());
                        }
                    }
                    case "classSetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.classSetters((Boolean) exp.getValue());
                        }
                    }
                    case "interfaceSetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.interfaceSetters((Boolean) exp.getValue());
                        }
                    }
                    case "base" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            builder.base((Boolean) exp.getValue());
                        }
                    }
//                    case "baseModifierClass":
//                        value = pair.getValue().asClassExpr().getTypeAsString();
//                        if (StringUtils.isNotBlank(value)) {
//                            builder.baseModifierClass(value);
//                        }
//                        break;
//                    case "mixInClass":
//                        value = pair.getValue().asClassExpr().getTypeAsString();
//                        if (StringUtils.isNotBlank(value) && !"void".equals(value)) {
//                            builder.mixInClass(value);
//                        }
//                        break;
                    case "implementationPackage" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp) {
                            var value = (String) exp.getValue();
                            if (StringUtils.isNotBlank(value)) {
                                builder.classPackage(value);
                            }
                        }
                    }
                    case "strategy" -> {
                        if (pair.getValue() instanceof PsiReferenceExpression exp) {
                            var value = exp.getReferenceName();
                            if (StringUtils.isNotBlank(value)) {
                                builder.strategy(GenerationStrategy.valueOf(value));
                            }
                        }
                    }
                    case "basePath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp) {
                            var value = (String) exp.getValue();
                            if (StringUtils.isNotBlank(value)) {
                                builder.basePath(value);
                            }
                        }
                    }
                    case "interfacePath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp) {
                            var value = (String) exp.getValue();
                            if (StringUtils.isNotBlank(value)) {
                                builder.interfacePath(value);
                            }
                        }
                    }
                    case "implementationPath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp) {
                            var value = (String) exp.getValue();
                            if (StringUtils.isNotBlank(value)) {
                                builder.implementationPath(value);
                            }
                        }
                    }
//                    case "enrichers":
//                        builder.predefinedEnrichers((List)handleClassExpression(pair.getValue(), List.class));
//                        break;
//                    case "inheritedEnrichers":
//                        builder.predefinedInheritedEnrichers((List)handleClassExpression(pair.getValue(), List.class));
//                        break;
//                    case "options":
//                        builder.options((Set)handleClassExpression(pair.getValue(), Set.class));
                    default -> builder.custom(pair.getAttributeName(), pair.getAttributeValue());
                }
            } else if (!(node instanceof Name)) {
                builder.custom("value", node);
            }
        });
    }

    public static void registerTemplate(PsiClass template) {
        defaultProperties.put(template.getQualifiedName(), () -> {
            var builder = defaultBuilder();

            Arrays.stream(template.getAnnotations())
                    .filter(a -> defaultProperties.containsKey(a.getQualifiedName()))
                    .forEach(a -> readAnnotation(a, builder));

            Arrays.stream(template.getMethods())
                    .filter(PsiAnnotationMethod.class::isInstance)
                    .map(PsiAnnotationMethod.class::cast)
                    .filter(m -> nonNull(m.getDefaultValue()))
                    .forEach(method -> {
                        switch (method.getName()) {
                            case "base" -> builder.base(handleBooleanExpression(method.getDefaultValue()));
                            case "name" -> builder.name(handleStringExpression(method.getDefaultValue()));
                            case "generateConstructor" -> builder.generateConstructor(handleBooleanExpression(method.getDefaultValue()));
//                            case "options" ->
//                                    builder.options(handleClassExpression(method.getDefaultValue().get(), Set.class));
                            case "interfaceName" -> builder.interfaceName(handleStringExpression(method.getDefaultValue()));
                            case "implementationPath" -> builder.implementationPath(handleStringExpression(method.getDefaultValue()));
//                            case "enrichers" ->
//                                    builder.predefinedEnrichers(handleClassExpression(method.getDefaultValue().get(), List.class));
//                            case "inheritedEnrichers" ->
//                                    builder.predefinedInheritedEnrichers(handleClassExpression(method.getDefaultValue().get(), List.class));
                            case "interfaceSetters" -> builder.interfaceSetters(handleBooleanExpression(method.getDefaultValue()));
                            case "classGetters" -> builder.classGetters(handleBooleanExpression(method.getDefaultValue()));
                            case "classSetters" -> builder.classSetters(handleBooleanExpression(method.getDefaultValue()));
//                            case "baseModifierClass" ->
//                                    builder.baseModifierClass(handleClassExpression(method.getDefaultValue().get()));
//                            case "mixInClass" ->
//                                    builder.mixInClass(handleClassExpression(method.getDefaultValue().get()));
                            case "interfacePath" -> builder.interfacePath(handleStringExpression(method.getDefaultValue()));
                            case "generateInterface" -> builder.generateInterface(handleBooleanExpression(method.getDefaultValue()));
                            case "basePath" -> builder.basePath(handleStringExpression(method.getDefaultValue()));
                            case "generateImplementation" -> builder.generateImplementation(handleBooleanExpression(method.getDefaultValue()));
                            case "implementationPackage" -> builder.classPackage(handleStringExpression(method.getDefaultValue()));
//                            case "strategy" ->
//                                    builder.strategy(handleEnumExpression(method.getDefaultValue().get(), GenerationStrategy.class));
                            default -> builder.custom(method.getName(), method.getDefaultValue());
                        }
                    });

            return builder;
        });
    }

    protected static boolean handleBooleanExpression(PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression exp) {
            return (Boolean) exp.getValue();
        }
        return false;
    }

    protected static String handleStringExpression(PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression exp) {
            return (String) exp.getValue();
        }
        return null;
    }

    @Builder
    @Data
    public static class LookupDescription {
        private PrototypeData prototype;
        private PsiClass cls;

        public boolean isPrototype() {
            return nonNull(prototype);
        }
    }

}
