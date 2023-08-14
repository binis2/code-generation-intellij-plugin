package net.binis.intellij.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import net.binis.intellij.tools.Binis;
import net.binis.intellij.tools.Lookup;
import net.binis.intellij.tools.objects.EnricherData;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;

import static com.intellij.openapi.util.NullUtils.notNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static net.binis.codegen.tools.Tools.with;

public class CodeGenAugmentProvider extends PsiAugmentProvider {

    protected static final Key<String> AUGMENTED = Key.create("AUGMENTED");

    protected static final ThreadLocal<Set<PsiElement>> _augmenting = new ThreadLocal<>();

    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                          @NotNull final Class<Psi> type) {
        return getAugments(element, type, null);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                          @NotNull final Class<Psi> type,
                                                          @Nullable String nameHint) {
        List<PsiElement> result = new ArrayList<>();
        if (!Lookup.isRegistering()) {
            try {
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
            } catch (IndexNotReadyException e) {
                //Do nothing
            }
        }

        return (List) result;
    }

    @SuppressWarnings("unchecked")
    private void checkForAnnotationAugments(List<PsiElement> result, PsiClass cls, Class<? extends PsiElement> type) {
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
                                            field.putUserData(AUGMENTED, field.getName());
                                            result.add(field);
                                        }
                                    }
                                    case METHOD -> {
                                        //Not implemented
                                    }
                                    case CONSTRUCTOR -> {
                                        //Not implemented
                                    }
                                }
                            })

                ));
    }

    private boolean filterByType(EnricherData data, Class<? extends PsiElement> type) {
        return switch (data.getAdds()) {
            case FIELD -> PsiField.class.isAssignableFrom(type);
            case METHOD -> PsiMethod.class.isAssignableFrom(type) && !PsiMethod.class.cast(type).isConstructor();
            case CONSTRUCTOR -> PsiMethod.class.isAssignableFrom(type) && PsiMethod.class.cast(type).isConstructor();
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
        var manager = cls.getContainingFile().getManager();
        var list = new LightModifierList(manager);
        var field = new LightFieldBuilder(data.getName(), data.getType(), cls).setModifierList(list);
        field.setContainingClass(cls);
        handleModifiers(list, data.getModifier());
        return field;
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
