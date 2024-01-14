package net.binis.intellij.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import net.binis.codegen.generation.core.Helpers;
import net.binis.intellij.CodeGenAnnotator;
import net.binis.intellij.tools.Binis;
import net.binis.intellij.tools.Lookup;
import net.binis.intellij.tools.objects.EnricherData;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

import static com.intellij.openapi.util.NullUtils.notNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static net.binis.codegen.tools.Tools.with;

public class CodeGenAugmentProvider extends PsiAugmentProvider {

    private final Logger log = Logger.getInstance(CodeGenAnnotator.class);
    protected static final Key<String> AUGMENTED = Key.create("AUGMENTED");
    protected static final ThreadLocal<Set<PsiElement>> _augmenting = new ThreadLocal<>();


    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                          @NotNull final Class<Psi> type,
                                                          @Nullable String nameHint) {
        List<PsiElement> result = new ArrayList<>();
        try {
            if (!DumbService.isDumb(element.getProject()) && Binis.isCodeGenUsed(element) && !Lookup.getRegisteringTemplate()) {
                if (element instanceof PsiClass cls && cls.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
                    if (cls.isAnnotationType()) {
                        if (PsiMethod.class.equals(type) && notNull(cls.getExtendsList()) && Binis.isAnnotationInheritanceEnabled(element.getProject())) {
                            List<PsiMethod> methods = (List) result;
                            var list = cls.getExtendsList().getReferencedTypes();
                            if (list.length > 0) {
                                Arrays.stream(list)
                                        .map(PsiClassType::resolve)
                                        .filter(Objects::nonNull)
                                        .forEach(t ->
                                                Arrays.stream(t.getAllMethods())
                                                        .filter(PsiAnnotationMethodImpl.class::isInstance)
                                                        .filter(m -> isNull(m.getUserData(AUGMENTED)))
                                                        .filter(m -> methods.stream().noneMatch(a ->
                                                                a.getName().equals(m.getName())))
                                                        //TODO: Use LightMethodBuilder
                                                        .map(PsiElement::copy)
                                                        .map(PsiMethod.class::cast)
                                                        .forEach(m -> {
                                                            m.putUserData(AUGMENTED, m.getName());
                                                            methods.add(m);
                                                        }));
                                cls.putUserData(AUGMENTED, cls.getQualifiedName());
                            }
                        }
                    } else {
                        if (PsiField.class.equals(type) && Binis.isBracketlessMethodsEnabled(element.getProject())) {
                            var augmenting = _augmenting.get();
                            if (!"_Dummy_".equals(cls.getQualifiedName()) && (isNull(augmenting) || !augmenting.contains(cls))) {
                                if (isNull(augmenting)) {
                                    augmenting = new HashSet<>();
                                    _augmenting.set(augmenting);
                                }
                                augmenting.add(cls);
                                try {
                                    Arrays.stream(cls.getAllMethods())
                                            .filter(m -> !m.hasParameters())
                                            .filter(m -> nonNull(m.getReturnType()))
                                            .map(this::createField)
                                            .forEach(m -> {
                                                m.putUserData(AUGMENTED, m.getName());
                                                result.add(m);
                                            });
                                    cls.putUserData(AUGMENTED, cls.getQualifiedName());
                                } finally {
                                    augmenting.remove(cls);
                                    if (augmenting.isEmpty()) {
                                        _augmenting.remove();
                                    }
                                }
                            }
                        }
                    }
                    checkForAnnotationAugments(result, cls, type);
                }
            }
        } catch (IndexNotReadyException e) {
            //Do nothing
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            log.warn(e);
        } catch (Throwable e) {
            log.warn(e);
        }
        return (List) result;
    }

    @SuppressWarnings("unchecked")
    protected void checkForAnnotationAugments(List<PsiElement> result, PsiClass cls, Class<? extends PsiElement> type) {
        if (!cls.isAnnotationType()) {
            with(Lookup.getPrototypeData(cls), proto ->
                    with((List<EnricherData>) proto.getCustom().get("enrichers"), list ->
                            list.stream()
                                    .filter(data -> nonNull(data.getAdds()))
                                    .filter(data -> filterByType(data, type))
                                    .forEach(data -> {
                                        switch (data.getAdds()) {
                                            case FIELD -> {
                                                if (StringUtils.isNotBlank(data.getName()) && StringUtils.isNotBlank(data.getType())) {
                                                    var field = createField(cls, data);
                                                    if (nonNull(field)) {
                                                        result.add(field);
                                                    }
                                                }
                                            }
                                            case METHOD -> {
                                                if (StringUtils.isNotBlank(data.getName()) && StringUtils.isNotBlank(data.getType())) {
                                                    var method = createMethod(cls, data, false);
                                                    if (nonNull(method)) {
                                                        result.add(method);
                                                    }
                                                }
                                            }
                                            case CONSTRUCTOR -> {
                                                var constructor = createMethod(cls, data, true);
                                                if (nonNull(constructor)) {
                                                    result.add(constructor);
                                                }
                                            }
                                            case GETTER ->
                                                getFields(cls).forEach(f -> {
                                                    var method = createGetter(cls, f);
                                                    if (nonNull(method)) {
                                                        result.add(method);
                                                    }
                                                });
                                            case SETTER ->
                                                    getFields(cls).filter(f -> !f.hasModifier(JvmModifier.STATIC)).forEach(f -> {
                                                        var method = createSetter(cls, f);
                                                        if (nonNull(method)) {
                                                            result.add(method);
                                                        }
                                                    });
                                        }
                                    })

                    ));
        }
    }

    protected PsiMethod createMethod(PsiClass cls, EnricherData data, boolean constructor) {
        var methods = getMethods(cls)
                .filter(m -> m.isConstructor() == constructor);

        if (!constructor) {
            methods = methods.filter(m -> m.getName().equals(data.getName()));
        }

        var builder = new LightMethodBuilder(cls.getManager(), JavaLanguage.INSTANCE, constructor ? cls.getName() : data.getName());
        builder.setConstructor(constructor);
        builder.setContainingClass(cls);
        builder.setNavigationElement(cls);
        var list = new LightModifierList(cls.getManager());
        handleModifiers(list, data.getModifier());
        if (nonNull(data.getFilter())) {
            data.getFilter().apply(cls).forEach(builder::addParameter);
        }
        with(data.getParameters(), params ->
                params.forEach(param ->
                        builder.addParameter(param.getName(), param.getType())));

        if (methods.filter(m -> (m.hasParameters() ? m.getParameters().length : 0) == builder.getParameterList().getParametersCount()).anyMatch(m -> {
            for (var i = 0; i < builder.getParameterList().getParametersCount(); i++) {
                var param = m.getParameters()[i];
                var dataParam = builder.getParameterList().getParameter(i);
                if (!dataParam.getType().getPresentableText().equals(((PsiType) param.getType()).getPresentableText())) {
                    return false;
                }
            }
            return true;
        })) {
            return null;
        }

        builder.putUserData(AUGMENTED, builder.getName());

        return builder;
    }

    protected PsiMethod createGetter(PsiClass cls, PsiField field) {
        var name = Helpers.getGetterName(field.getName(), field.getType().getCanonicalText());

        if (getMethods(cls).filter(m -> !m.hasParameters()).noneMatch(m -> m.getName().equals(name))) {
            var builder = new LightMethodBuilder(cls.getManager(), JavaLanguage.INSTANCE, name);
            builder.setContainingClass(cls);
            builder.setNavigationElement(cls);
            builder.setMethodReturnType(field.getType());
            builder.addModifier(PsiModifier.PUBLIC);

            builder.putUserData(AUGMENTED, builder.getName());
            return builder;
        } else {
            return null;
        }
    }

    protected PsiMethod createSetter(PsiClass cls, PsiField field) {
        var name = Helpers.getSetterName(field.getName());
        if (getMethods(cls)
                .filter(PsiMethod::hasParameters)
                .filter(m -> m.getParameterList().getParametersCount() == 1)
                .filter(m -> m.getParameterList().getParameter(0).getType().getCanonicalText().equals(field.getType().getCanonicalText()))
                .noneMatch(m -> m.getName().equals(name))) {
            var builder = new LightMethodBuilder(cls.getManager(), JavaLanguage.INSTANCE, name);
            builder.setContainingClass(cls);
            builder.setNavigationElement(cls);
            builder.addModifier(PsiModifier.PUBLIC);
            builder.setMethodReturnType(PsiTypes.voidType());
            builder.addParameter(field.getName(), field.getType());

            builder.putUserData(AUGMENTED, builder.getName());
            return builder;
        } else {
            return null;
        }
    }

    protected Stream<PsiMethod> getMethods(PsiClass cls) {
        return (cls instanceof PsiExtensibleClass ext ? ext.getOwnMethods().stream() : Arrays.stream(cls.getMethods()));
    }

    protected Stream<PsiField> getFields(PsiClass cls) {
        return (cls instanceof PsiExtensibleClass ext ? ext.getOwnFields().stream() : Arrays.stream(cls.getFields()));
    }

    protected boolean filterByType(EnricherData data, Class<? extends PsiElement> type) {
        return switch (data.getAdds()) {
            case FIELD -> PsiField.class.isAssignableFrom(type);
            case METHOD, CONSTRUCTOR, GETTER, SETTER -> PsiMethod.class.isAssignableFrom(type);
            default -> false;
        };
    }

    protected PsiField createField(PsiMethod method) {
        //TODO: Use LightFieldBuilder
        var factory = JavaPsiFacade.getElementFactory(method.getProject());
        var field = factory.createField(method.getName(), method.getReturnType());
        field.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        return field;
    }

    protected PsiField createField(PsiClass cls, EnricherData data) {
        if (getFields(cls).noneMatch(f -> f.getName().equals(data.getName()))) {
            var manager = cls.getContainingFile().getManager();
            var list = new LightModifierList(manager);
            var field = new LightFieldBuilder(data.getName(), data.getType(), cls).setModifierList(list);
            field.setContainingClass(cls);
            handleModifiers(list, data.getModifier());
            return field;
        } else {
            return null;
        }
    }

    private void handleModifiers(LightModifierList builder, long modifier) {
        if ((modifier & Modifier.PUBLIC) != 0) {
            builder.addModifier(PsiModifier.PUBLIC);
        }
        if ((modifier & Modifier.PRIVATE) != 0) {
            builder.addModifier(PsiModifier.PRIVATE);
        }
        if ((modifier & Modifier.PROTECTED) != 0) {
            builder.addModifier(PsiModifier.PROTECTED);
        }
        if ((modifier & Modifier.STATIC) != 0) {
            builder.addModifier(PsiModifier.STATIC);
        }
        if ((modifier & Modifier.FINAL) != 0) {
            builder.addModifier(PsiModifier.FINAL);
        }
    }

}
