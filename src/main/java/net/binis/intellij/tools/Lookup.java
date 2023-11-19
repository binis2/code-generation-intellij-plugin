package net.binis.intellij.tools;

import com.github.javaparser.ast.expr.Name;
import com.intellij.lang.jvm.JvmAnnotation;
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.lang.jvm.annotation.JvmNestedAnnotationValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.task.ProjectTaskManager;
import lombok.Builder;
import lombok.Data;
import net.binis.codegen.annotation.CodePrototypeTemplate;
import net.binis.codegen.annotation.EnumPrototype;
import net.binis.codegen.annotation.augment.AugmentTargetType;
import net.binis.codegen.annotation.augment.AugmentTargetTypeSeverity;
import net.binis.codegen.annotation.augment.AugmentType;
import net.binis.codegen.annotation.augment.CodeAugment;
import net.binis.codegen.annotation.type.GenerationStrategy;
import net.binis.codegen.discovery.Discoverer;
import net.binis.codegen.generation.core.interfaces.PrototypeData;
import net.binis.codegen.tools.Holder;
import net.binis.codegen.tools.Interpolator;
import net.binis.intellij.objects.CodeGenLightParameter;
import net.binis.intellij.services.CodeGenProjectService;
import net.binis.intellij.tools.objects.EnricherData;
import net.binis.intellij.util.PrototypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.concurrency.Promise;

import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static net.binis.codegen.generation.core.Structures.*;
import static net.binis.codegen.tools.Tools.*;

public class Lookup {

    private static final Logger log = Logger.getInstance(Lookup.class);
    private static final ThreadLocal<Boolean> registeringTemplate = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> registeringClass = ThreadLocal.withInitial(() -> false);
    private static final Map<String, LookupDescription> classes = new ConcurrentHashMap<>();
    private static final Map<String, PrototypeData> prototypes = new ConcurrentHashMap<>();
    private static Set<String> nonTemplates = initNonTemplates();
    private static final Set<String> processing = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, String> generated = new ConcurrentHashMap<>();
    private static final Set<String> nonGenerated = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, ValidationDescription> validators = new ConcurrentHashMap<>();
    public static final Set<String> STARTERS = Set.of("create", "with", "find", "builder");
    private static final Map<String, List<String>> knownTargetAwareClasses = Map.of(
            "net.binis.codegen.validation.consts.ValidationTargets.Primitives", List.of(int.class.getCanonicalName(), long.class.getCanonicalName(), double.class.getCanonicalName(), float.class.getCanonicalName(), short.class.getCanonicalName(), byte.class.getCanonicalName(), boolean.class.getCanonicalName(), char.class.getCanonicalName()),
            "net.binis.codegen.validation.consts.ValidationTargets.Wrappers", List.of(Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Double.class.getCanonicalName(), Float.class.getCanonicalName(), Short.class.getCanonicalName(), Byte.class.getCanonicalName(), Boolean.class.getCanonicalName(), Character.class.getCanonicalName()),
            "net.binis.codegen.validation.consts.ValidationTargets.Common", List.of(int.class.getCanonicalName(), long.class.getCanonicalName(), double.class.getCanonicalName(), float.class.getCanonicalName(), short.class.getCanonicalName(), byte.class.getCanonicalName(), boolean.class.getCanonicalName(), char.class.getCanonicalName(), Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Double.class.getCanonicalName(), Float.class.getCanonicalName(), Short.class.getCanonicalName(), Byte.class.getCanonicalName(), Boolean.class.getCanonicalName(), Character.class.getCanonicalName(), String.class.getCanonicalName()),
            "net.binis.codegen.validation.consts.ValidationTargets.WrapperNumbers", List.of(Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Double.class.getCanonicalName(), Float.class.getCanonicalName(), Short.class.getCanonicalName(), Byte.class.getCanonicalName()),
            "net.binis.codegen.validation.consts.ValidationTargets.PrimitiveNumbers", List.of(int.class.getCanonicalName(), long.class.getCanonicalName(), double.class.getCanonicalName(), float.class.getCanonicalName(), short.class.getCanonicalName(), byte.class.getCanonicalName()),
            "net.binis.codegen.validation.consts.ValidationTargets.Numbers", List.of(int.class.getCanonicalName(), long.class.getCanonicalName(), double.class.getCanonicalName(), float.class.getCanonicalName(), short.class.getCanonicalName(), byte.class.getCanonicalName(), Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Double.class.getCanonicalName(), Float.class.getCanonicalName(), Short.class.getCanonicalName(), Byte.class.getCanonicalName())
    );


    public static final Map<Project, CodeGenProjectService> projects = new ConcurrentHashMap<>();

    private static Set<String> initNonTemplates() {
        var result = new HashSet<String>();
        result.add(Target.class.getCanonicalName());
        result.add(Retention.class.getCanonicalName());
        result.add(Documented.class.getCanonicalName());
        result.add(Inherited.class.getCanonicalName());
        result.add(Repeatable.class.getCanonicalName());
        result.add(Native.class.getCanonicalName());
        result.add(Collections.class.getCanonicalName());
        result.add(Map.class.getCanonicalName());
        result.add(Set.class.getCanonicalName());
        result.add(List.class.getCanonicalName());
        return Collections.synchronizedSet(result);
    }

    public static boolean getRegisteringTemplate() {
        return registeringTemplate.get() || registeringClass.get();
    }

    public static synchronized void registerClass(PsiClass cls) {
        try {
            var name = cls.getQualifiedName();
            if (nonNull(name) && !classes.containsKey(name)) {
                registeringClass.set(true);
                try {
                    classes.put(name, LookupDescription.builder()
                            .clsName(cls.getQualifiedName())
                            .prototype(Arrays.stream(cls.getAnnotations())
                                    .map(a -> {
                                        var data = isPrototypeAnnotation(a);
                                        if (nonNull(data)) {
                                            var builder = copyData(data);
                                            readAnnotation(a, builder);
                                            return Optional.of(builder.build());
                                        } else {
                                            return Optional.<PrototypeData>empty();
                                        }
                                    })
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .findFirst()
                                    .orElse(null))
                            .build());
                    checkGenerated(cls);
                    log.info("Registered class: " + name);
                } finally {
                    registeringClass.set(false);
                }
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to register class: " + cls.getQualifiedName() + " - " + e.getMessage(), e);
        } finally {
            projects.computeIfAbsent(cls.getProject(), p ->
                    p.getService(CodeGenProjectService.class));
        }
    }

    public static PrototypeData getPrototypeData(PsiClass cls) {
        return getPrototypeData(cls.getQualifiedName());
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
        try {
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
        } catch (Exception e) {
            return false;
        }
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
        var name = cls.getQualifiedName();
        if (generated.containsKey(name)) {
            return true;
        }
        if (!nonGenerated.contains(name)) {
            var ann = cls.getAnnotation("javax.annotation.processing.Generated");
            if (nonNull(ann)) {
                var value = ann.findAttributeValue("value");
                if (value instanceof PsiLiteralExpression exp) {
                    var proto = (String) exp.getValue();
                    findClass(proto).ifPresent(Lookup::registerClass);
                    generated.put(name, proto);
                    log.info("Registered generated class: " + name);
                    return true;
                }
            }
            log.info("Registered non-generated class: " + name);
            nonGenerated.add(name);
        }

        return false;
    }

    public static String getPrototype(String name) {
        return generated.get(name);
    }

    public static String getPrototype(PsiClass cls) {
        return getPrototype(cls.getQualifiedName());
    }

    public static PsiClass getPrototypeClass(String name) {
        var proto = generated.get(name);
        if (nonNull(proto)) {
            var cls = classes.get(proto);
            if (nonNull(cls)) {
                return findClass(cls.clsName).orElse(null);
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
        return isPrototypeAnnotation(proto.getQualifiedName());
    }

    public static PrototypeData isPrototypeAnnotation(String name) {
        if (nonNull(name)) {
            var result = prototypes.get(name);

            if (nonNull(result)) {
                return result;
            }

            return discoverAnnotation(name);
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
                                            .filter(d -> !prototypes.containsKey(d.getName()))
                                            .forEach(service ->
                                                    Lookup.processPrototype(service.getName()));
                                    var result = prototypes.get(name);
                                    if (nonNull(result)) {
                                        return result;
                                    } else {
                                        checkForNonRegisteredTemplates(cls.get());
                                        result = prototypes.get(name);
                                        if (nonNull(result)) {
                                            return result;
                                        }
                                    }
                                }
                            }
                        } else {
                            checkForNonRegisteredTemplates(cls.get());
                            var result = prototypes.get(name);
                            if (nonNull(result)) {
                                return result;
                            }
                        }
                    }
                }
            } finally {
                processing.remove(name);
            }
        }

        return null;
    }

    protected static void checkForNonRegisteredTemplates(PsiClass cls) {
        for (var ann : cls.getAnnotations()) {
            var name = ann.getQualifiedName();
            if (CodePrototypeTemplate.class.getCanonicalName().equals(name)) {
                prototypes.computeIfAbsent(cls.getQualifiedName(), k -> {
                    registerTemplate(cls);
                    return defaultProperties.get(k).get().build();
                });
            } else if (!nonTemplates.contains(name)) {
                if (!prototypes.containsKey(name) && !processing.contains(name)) {
                    try {
                        processing.add(name);
                        findClass(name).ifPresent(Lookup::checkForNonRegisteredTemplates);
                    } finally {
                        processing.remove(name);
                    }
                }

                if (prototypes.containsKey(name)) {
                    name = cls.getQualifiedName();
                    registerTemplate(cls);
                    prototypes.put(name, defaultProperties.get(name).get().build());
                } else {
                    registerNonTemplate(name);
                }
            }
        }
    }

    private static void registerNonTemplate(String name) {
        log.info("Registered non-template: " + name);
        nonTemplates.add(name);
    }

    public static boolean processPrototype(String name) {
        var clas = findClass(name);
        if (clas.isPresent()) {
            if ("net.binis.codegen.annotation.CodePrototype".equals(name) || "net.binis.codegen.annotation.EnumPrototype".equals(name)) {
                return nonNull(prototypes.computeIfAbsent(name, k -> {
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
                    prototypes.put(name, defaultProperties.get(name).get().build());
                } else {
                    registerNonTemplate(name);
                }
                return true;
            }
        }

        return false;
    }

    public static String getGeneratedName(PsiClass cls) {
        return getGeneratedName(cls.getQualifiedName(), cls.getParent() instanceof PsiClass);
    }

    public static String getGeneratedName(String name) {
        var cls = findClass(name);
        return getGeneratedName(name, cls.map(c ->
                c.getParent() instanceof PsiClass).orElse(false));
    }

    public static String getGeneratedName(String name, boolean isNested) {
        var className = "";
        var classPath = "";
        var impl = false;
        var proto = getPrototypeData(name);
        if (nonNull(proto)) {
            if (GenerationStrategy.IMPLEMENTATION.equals(proto.getStrategy())) {
                className = proto.getClassName();
                classPath = proto.getClassPackage();
                impl = true;
            } else {
                className = proto.getInterfaceName();
                classPath = proto.getInterfacePackage();
            }

            if (StringUtils.isNotBlank(className)) {
                if (StringUtils.isNotBlank(classPath)) {
                    return classPath + "." + className;
                } else {
                    name = name.substring(0, name.lastIndexOf(".") + 1) + className;
                    impl = false;
                }
            } else if (StringUtils.isNotBlank(classPath)) {
                name = classPath + "." + name.substring(name.lastIndexOf(".") + 1);
            }
        }

        if (isNested) {
            var cls = name.substring(name.lastIndexOf("."));
            name = name.substring(0, name.lastIndexOf("."));
            name = name.substring(0, name.lastIndexOf(".")) + cls;
        }
        if (name.endsWith("Prototype")) {
            name = name.substring(0, name.length() - 9);
            if (name.endsWith("Entity")) {
                name = name.substring(0, name.length() - 6);
            }
        }
        name = name.replace(".prototype.", ".");

        if (impl) {
            name = name + "Impl";
        }

        return name;
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
        builder.custom("prototype", ann.getQualifiedName());
        ann.getAttributes().forEach(node -> {
            if (node instanceof PsiNameValuePair pair) {
                switch (pair.getAttributeName()) {
                    case "name" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            if (StringUtils.isNotBlank(value)) {
                                var intf = value.replace("Entity", "");
                                builder.name(value).className(value).interfaceName(intf).longModifierName(intf + ".Modify");
                            }
                        }
                    }
                    case "generateConstructor" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.generateConstructor(value);
                        }
                    }
                    case "generateImplementation" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.generateImplementation(value);
                        }
                    }
                    case "generateInterface" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.generateInterface(value);
                        }
                    }
                    case "interfaceName" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            builder.interfaceName(value);
                        }
                    }
                    case "classGetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.classGetters(value);
                        }
                    }
                    case "classSetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.classSetters(value);
                        }
                    }
                    case "interfaceSetters" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.interfaceSetters(value);
                        }
                    }
                    case "base" -> {
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof Boolean value) {
                            builder.base(value);
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
                        if (pair.getValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            if (StringUtils.isNotBlank(value)) {
                                builder.classPackage(value);
                            }
                        }
                    }
                    case "strategy" ->
                            nullCheck(PrototypeUtil.readPrototypeStrategy(pair.getValue()), builder::strategy);
                    case "basePath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            if (StringUtils.isNotBlank(value)) {
                                builder.basePath(value);
                            }
                        }
                    }
                    case "interfacePath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            if (StringUtils.isNotBlank(value)) {
                                builder.interfacePath(value);
                            }
                        }
                    }
                    case "implementationPath" -> {
                        if (pair.getAttributeValue() instanceof PsiLiteralExpression exp && exp.getValue() instanceof String value) {
                            if (StringUtils.isNotBlank(value)) {
                                builder.implementationPath(value);
                            }
                        }
                    }
                    case "enrichers" -> handleEnrichers(builder, pair);
                    case "inheritedEnrichers" -> handleInheritedEnrichers(builder, pair);
//                    case "options":
//                        builder.options((Set)handleClassExpression(pair.getValue(), Set.class));
                    default -> builder.custom(pair.getAttributeName(), pair.getAttributeValue());
                }
            } else if (!(node instanceof Name)) {
                builder.custom("value", node);
            }
        });
    }

    protected static void handleInheritedEnrichers(PrototypeDataHandler.PrototypeDataHandlerBuilder builder, PsiNameValuePair pair) {
        with(handleEnrichersValue(pair.getValue()), list -> builder.custom("inheritedEnrichers", list));
    }

    protected static void handleInheritedEnrichers(PrototypeDataHandler.PrototypeDataHandlerBuilder builder, PsiAnnotationMemberValue value) {
        with(handleEnrichersValue(value), list -> builder.custom("inheritedEnrichers", list));
    }

    protected static void handleEnrichers(PrototypeDataHandler.PrototypeDataHandlerBuilder builder, PsiNameValuePair pair) {
        with(handleEnrichersValue(pair.getValue()), list -> builder.custom("enrichers", list));
    }

    protected static void handleEnrichers(PrototypeDataHandler.PrototypeDataHandlerBuilder builder, PsiAnnotationMemberValue value) {
        with(handleEnrichersValue(value), list -> builder.custom("enrichers", list));
    }


    protected static List<EnricherData> handleEnrichersValue(PsiAnnotationMemberValue value) {
        var result = new ArrayList<EnricherData>();
        handleEnrichersValue(result, value);
        return result;
    }

    protected static void handleEnrichersValue(List<EnricherData> list, PsiAnnotationMemberValue value) {
        if (value instanceof PsiClassObjectAccessExpression exp && (exp.getType() instanceof PsiImmediateClassType || exp.getType() instanceof PsiClass || exp.getType() instanceof PsiClassReferenceType)) {
            findClass(exp.getOperand().getType().getCanonicalText())
                    .ifPresent(cls -> list.add(buildEnricherData(cls)));
        } else if (value instanceof PsiArrayInitializerMemberValue exp) {
            for (var val : exp.getInitializers()) {
                handleEnrichersValue(list, val);
            }
        }
    }

    protected static EnricherData buildEnricherData(PsiClass cls) {
        var result = EnricherData.builder().cls(cls);
        var ann = cls.getAnnotation(CodeAugment.class.getCanonicalName());
        if (nonNull(ann)) {
            ann.getAttributes().forEach(attr -> {
                switch (attr.getAttributeName()) {
                    case "adds" ->
                            result.adds(PrototypeUtil.readAnnotationEnumValue(attr.getAttributeValue(), AugmentType.class));
                    case "targets" ->
                            result.targets(PrototypeUtil.readAnnotationEnumListValue(attr.getAttributeValue(), AugmentTargetType.class));
                    case "severity" ->
                            result.severity(nullCheck(PrototypeUtil.readAnnotationEnumValue(attr.getAttributeValue(), AugmentTargetTypeSeverity.class), AugmentTargetTypeSeverity.ERROR));
                    case "name" -> {
                        if (attr.getAttributeValue() instanceof JvmAnnotationConstantValue exp && exp.getConstantValue() instanceof String value && StringUtils.isNotBlank(value)) {
                            result.name(value);
                        }
                    }
                    case "type" -> {
                        if (attr.getAttributeValue() instanceof JvmAnnotationConstantValue exp && exp.getConstantValue() instanceof String value && StringUtils.isNotBlank(value)) {
                            result.type(value);
                        }
                    }
                    case "description" -> {
                        if (attr.getAttributeValue() instanceof JvmAnnotationConstantValue exp && exp.getConstantValue() instanceof String value && StringUtils.isNotBlank(value)) {
                            result.description(value);
                        }
                    }
                    case "modifier" -> {
                        if (attr.getAttributeValue() instanceof JvmAnnotationConstantValue exp && exp.getConstantValue() instanceof Long value) {
                            result.modifier(value);
                        }
                    }
                    case "parameters" -> {
                        if (attr.getAttributeValue() instanceof JvmNestedAnnotationValue value) {
                            with(value.getValue(), params ->
                                    params.getAttributes().forEach(parAttr -> {
                                        switch (parAttr.getAttributeName()) {
                                            case "filter":
                                                with(parAttr.getAttributeValue(), v ->
                                                        PrototypeUtil.readAnnotationConstantValue(v, c ->
                                                                result.filter(buildFilter(c.toString()))));
                                                break;
                                            case "suppresses":
                                                with(parAttr.getAttributeValue(), v ->
                                                        PrototypeUtil.readAnnotationConstantValue(v, c ->
                                                                result.paramsSuppresses(Interpolator.build(c.toString()))));
                                                break;
                                            case "params":
                                                if (parAttr.getAttributeValue() instanceof JvmNestedAnnotationValue pars) {
                                                    var list = new ArrayList<EnricherData.Parameter>();
                                                    with(pars.getValue(), p -> list.add(processParam(p)));
                                                    result.parameters(list);
                                                } else if (parAttr.getAttributeValue() instanceof JvmAnnotationArrayValue pars) {
                                                    var list = new ArrayList<EnricherData.Parameter>();
                                                    pars.getValues().forEach(v -> {
                                                        if (v instanceof JvmNestedAnnotationValue val) {
                                                            list.add(processParam(val.getValue()));
                                                            result.parameters(list);
                                                        }
                                                    });
                                                }
                                                break;
                                            default:
                                        }
                                    }));
                        }
                    }
                }
            });
        }
        return result.build();
    }

    private static EnricherData.Parameter processParam(JvmAnnotation param) {
        var result = EnricherData.Parameter.builder();
        param.getAttributes().forEach(a -> {
            switch (a.getAttributeName()) {
                case "name":
                    with(a.getAttributeValue(), v ->
                            PrototypeUtil.readAnnotationConstantValue(v, c ->
                                    result.name(c.toString())));
                    break;
                case "type":
                    with(a.getAttributeValue(), v ->
                            PrototypeUtil.readAnnotationConstantValue(v, c ->
                                    result.type(c.toString())));
                    break;
                default:
            }
        });

        return result.build();
    }

    @SuppressWarnings("unchecked")
    protected static Function<PsiClass, Stream<PsiParameter>> buildFilter(String filter) {
        var segments = filter.split("\\|");
        Function<PsiClass, Stream<CodeGenLightParameter>> elements = switch (segments[0]) {
            case "FIELDS" -> (PsiClass cls) ->
                    Arrays.stream(cls.getFields()).map(f ->
                            new CodeGenLightParameter(f.getName(), f.getType(), f.getParent(), f.getModifierList(), f.hasInitializer()));
            case "METHODS" -> (PsiClass cls) ->
                    Arrays.stream(cls.getMethods()).map(m ->
                            new CodeGenLightParameter(m.getName(), m.getReturnType(), m.getParent(), m.getModifierList(), false));
            default -> null;
        };

        if (nonNull(elements)) {
            var filters = new ArrayList<UnaryOperator<Stream<CodeGenLightParameter>>>();
            for (var i = 1; i < segments.length; i++) {
                if (nonNull(segments[i])) {
                    switch (segments[i]) {
                        case "PUBLIC" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.PUBLIC))));
                        case "PRIVATE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.PRIVATE))));
                        case "PROTECTED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.PROTECTED))));
                        case "STATIC" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.STATIC))));
                        case "FINAL" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.FINAL))));
                        case "ABSTRACT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.ABSTRACT))));
                        case "TRANSIENT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.TRANSIENT))));
                        case "VOLATILE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.VOLATILE))));
                        case "SYNCHRONIZED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.SYNCHRONIZED))));
                        case "NATIVE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.NATIVE))));
                        case "STRICTFP" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.STRICTFP))));
                        case "DEFAULT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> list.hasExplicitModifier(PsiModifier.DEFAULT))));
                        case "INITIALIZED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(CodeGenLightParameter::isInitializer));
                        case "!PUBLIC" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.PUBLIC))));
                        case "!PRIVATE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.PRIVATE))));
                        case "!PROTECTED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.PROTECTED))));
                        case "!STATIC" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.STATIC))));
                        case "!FINAL" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.FINAL))));
                        case "!ABSTRACT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.ABSTRACT))));
                        case "!TRANSIENT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.TRANSIENT))));
                        case "!VOLATILE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.VOLATILE))));
                        case "!SYNCHRONIZED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.SYNCHRONIZED))));
                        case "!NATIVE" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.NATIVE))));
                        case "!STRICTFP" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.STRICTFP))));
                        case "!DEFAULT" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> withRes(m.getOrgModifierList(), list -> !list.hasExplicitModifier(PsiModifier.DEFAULT))));
                        case "!INITIALIZED" ->
                                filters.add((Stream<CodeGenLightParameter> stream) -> stream.filter(m -> !m.isInitializer()));
                    }
                }
            }
            return (PsiClass c) -> {
                var result = elements.apply(c);
                for (var f : filters) {
                    result = f.apply(result);
                }
                return (Stream) result;
            };
        }

        return null;
    }

    public static void registerTemplate(PsiClass regTemplate) {
        var name = regTemplate.getQualifiedName();
        if (!defaultProperties.containsKey(name)) {
            registeringTemplate.set(true);
            try {
                log.info("Registering template '" + name + "'!");
                var holder = Holder.of(regTemplate);
                defaultProperties.put(name, () -> {
                    var template = holder.get();
                    try {
                        var parent = Arrays.stream(template.getAnnotations())
                                .filter(a -> defaultProperties.containsKey(a.getQualifiedName()))
                                .findFirst();

                        var builder = parent.isPresent() ? defaultProperties.get(parent.get().getQualifiedName()).get() : defaultBuilder();

                        parent.ifPresent(a ->
                                readAnnotation(a, builder));

                        if (EnumPrototype.class.getCanonicalName().equals(name)) {
                            builder.custom("enum", true);
                        }

                        var methods = template instanceof PsiExtensibleClass ext ? ext.getOwnMethods().stream() : Arrays.stream(template.getMethods());

                        methods.filter(PsiAnnotationMethod.class::isInstance)
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
                                        case "enrichers" -> handleEnrichers(builder, method.getDefaultValue());
                                        case "inheritedEnrichers" -> handleInheritedEnrichers(builder, method.getDefaultValue());
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
                                        case "strategy" -> builder.strategy(handleEnumExpression(method.getDefaultValue(), GenerationStrategy.class));
                                        default -> builder.custom(method.getName(), method.getDefaultValue());
                                    }
                                });

                        return builder;
                    } catch (PsiInvalidElementAccessException e) {
                        holder.set(findClass(name).orElseThrow(() -> new IllegalStateException("Unable to refresh template - " + name, e)));
                        return defaultProperties.get(name).get();
                    }
                });
                prototypes.clear();
                classes.clear();
                generated.clear();
                nonTemplates = initNonTemplates();
            } finally {
                registeringTemplate.set(false);
            }
        }
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

    @SuppressWarnings("unchecked")
    protected static <T extends Enum> T handleEnumExpression(PsiAnnotationMemberValue value, Class<T> enumClass) {
        try {
            return (T) Enum.valueOf(enumClass, value.getText().substring(value.getText().lastIndexOf('.') + 1));
        } catch (Exception e) {
            return null;
        }
    }


    public static Set<Module> refreshCache(Project project, VirtualFile file) {
        var result = new HashSet<Module>();

        with(PsiTreeUtil.getChildrenOfType(PsiManager.getInstance(project).findFile(file), PsiClass.class), array ->
                Arrays.stream(array)
                        .forEach(cls -> {
                            var proto = false;
                            var clazz = false;
                            var name = cls.getQualifiedName();

                            if (nonNull(classes.remove(name))) {
                                log.info("Removing class '" + name + "' from classes cache!");
                                clazz = true;
                            }
                            if (nonNull(prototypes.remove(name))) {
                                log.info("Removing class '" + name + "' from prototypes cache!");
                                proto = true;
                            }
                            if (nonTemplates.remove(name)) {
                                log.info("Removing class '" + name + "' from nonTemplates cache!");
                            }
                            if (nonNull(generated.remove(name))) {
                                log.info("Removing class '" + name + "' from generated cache!");
                                clazz = true;
                            }
                            if (nonGenerated.remove(name)) {
                                log.info("Removing class '" + name + "' from nonGenerated cache!");
                                clazz = true;
                            }
                            if (proto) {
                                Lookup.processPrototype(name);
                            }
                            if (clazz) {
                                Lookup.findClass(name).ifPresent(Lookup::registerClass);
                            }
                        }));

        return result;
    }

    public static Module getModule(PsiElement element) {
        return ProjectFileIndex.getInstance(element.getProject()).getModuleForFile(element.getContainingFile().getVirtualFile());
    }

    public static Promise<ProjectTaskManager.Result> rebuildModule(Module module) {
        return withRes(module.getProject(), project ->
                ProjectTaskManager.getInstance(project).build(module));
    }

    private static PrototypeDataHandler.PrototypeDataHandlerBuilder copyData(PrototypeData data) {
        var result = PrototypeDataHandler.builder()
                .base(data.isBase())
                .name(data.getName())
                .generateConstructor(data.isGenerateConstructor())
                .interfaceName(data.getInterfaceName())
                .interfaceSetters(data.isInterfaceSetters())
                .classGetters(data.isClassGetters())
                .classSetters(data.isClassSetters())
                .generateInterface(data.isGenerateInterface())
                .generateImplementation(data.isGenerateImplementation())
                .classPackage(data.getClassPackage())
                .strategy(data.getStrategy())
                .basePath(data.getBasePath())
                .interfacePath(data.getInterfacePath())
                .implementationPath(data.getImplementationPath())
                .enrichers(withRes(data.getEnrichers(), ArrayList::new))
                .inheritedEnrichers(withRes(data.getInheritedEnrichers(), ArrayList::new));
        with(data.getCustom(), custom -> custom.forEach(result::custom));
        return result;
    }

    public static ValidationDescription isValidationAnnotation(String name) {
        var data = validators.get(name);

        if (isNull(data)) {
            data = registerValidator(name);
        }

        return data.isValidationAnnotation() ? data : null;
    }

    public static boolean isEnum(PrototypeData data) {
        return withRes(data.getCustom(), custom -> withRes(custom.get("enum"), Boolean.class::cast, false), false);
    }

    public static ValidationDescription registerValidator(String name) {
        if (validators.size() < 3) {
            validators.compute("net.binis.codegen.annotation.validation.Validate", (k, v) ->
                    ValidationDescription.builder()
                            .cls(findClass(k).orElse(null))
                            .validator(true)
                            .targets(Collections.emptyList())
                            .build());
            validators.compute("net.binis.codegen.annotation.validation.Sanitize", (k, v) ->
                    ValidationDescription.builder()
                            .cls(findClass(k).orElse(null))
                            .sanitizer(true)
                            .targets(Collections.emptyList())
                            .build());
            validators.compute("net.binis.codegen.annotation.validation.Execute", (k, v) ->
                    ValidationDescription.builder()
                            .cls(findClass(k).orElse(null))
                            .executor(true)
                            .targets(Collections.emptyList())
                            .build());
        }

        processing.add(name);
        try {
            var data = validators.get(name);
            if (isNull(data)) {
                var cls = findClass(name);
                if (cls.isPresent() && cls.get().isAnnotationType()) {
                    for (var ann : cls.get().getAnnotations()) {
                        if (!processing.contains(ann.getQualifiedName())) {
                            var d = validators.get(ann.getQualifiedName());
                            if (isNull(d)) {
                                d = registerValidator(ann.getQualifiedName());
                            }
                            if (d.isValidationAnnotation()) {
                                var targets = d.getTargets();
                                var methods = cls.get().findMethodsByName("targets", false);
                                if (methods.length > 0) {
                                    targets = processTargets(null);
                                } else {
                                    targets = withRes(processTargets(ann.findAttributeValue("targets")), value -> value, targets);
                                }

                                d = ValidationDescription.builder()
                                        .cls(cls.get())
                                        .validator(d.isValidator())
                                        .sanitizer(d.isSanitizer())
                                        .executor(d.isExecutor())
                                        .targets(targets)
                                        .build();
                                validators.put(name, d);
                                return d;
                            }
                        }
                    }
                }
                data = ValidationDescription.builder().build();
            }

            return data;
        } finally {
            processing.remove(name);
        }
    }

    public static ValidationDescription processTargets(PsiAnnotation annotation, ValidationDescription data) {
        if (nonNull(data.cls)) {
            var attr = annotation.findAttributeValue("targets");
            if (nonNull(attr)) {
                return ValidationDescription.builder()
                        .cls(data.cls)
                        .validator(data.validator)
                        .sanitizer(data.sanitizer)
                        .executor(data.executor)
                        .targets(processTargets(attr))
                        .build();
            }
        }
        return data;
    }

    protected static List<String> processTargets(PsiAnnotationMemberValue attr) {
        if (nonNull(attr)) {
            if (attr instanceof PsiArrayInitializerMemberValue init) {
                var result = new ArrayList<String>();
                for (var i : init.getInitializers()) {
                    if (i instanceof PsiClassObjectAccessExpression exp) {
                        processTarget(result, exp.getOperand().getType().getCanonicalText());
                    }
                }
                return result;
            }
            if (attr instanceof PsiClassObjectAccessExpression exp) {
                var result = new ArrayList<String>();
                processTarget(result, exp.getOperand().getType().getCanonicalText());
                return result;
            }
        }

        return null;
    }

    protected static void processTarget(List<String> result, String name) {
        findClass(name).ifPresentOrElse(cls ->
                Arrays.stream(cls.getImplementsListTypes())
                        .filter(t -> "net.binis.codegen.validation.consts.ValidationTargets.TargetsAware".equals(t.getCanonicalText()))
                        .findFirst()
                        .ifPresentOrElse(t ->
                                processTargetAware(result, name), () -> result.add(name)), () -> result.add(name));
    }

    protected static void processTargetAware(List<String> result, String name) {
        //TODO: Proper target aware handling
        with(knownTargetAwareClasses.get(name), result::addAll);
    }

    @Builder
    @Data
    public static class LookupDescription {
        private PrototypeData prototype;
        private String clsName;

        public boolean isPrototype() {
            return nonNull(prototype);
        }
    }

    @Builder
    @Data
    public static class ValidationDescription {
        private PsiClass cls;
        private List<String> targets;
        private boolean executor;
        private boolean validator;
        private boolean sanitizer;

        public boolean isValidationAnnotation() {
            return executor || validator || sanitizer;
        }
    }


}
