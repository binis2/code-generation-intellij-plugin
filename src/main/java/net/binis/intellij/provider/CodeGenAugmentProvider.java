package net.binis.intellij.provider;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import net.binis.intellij.tools.Binis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NullUtils.notNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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
        var result = Collections.<PsiElement>emptyList();
        if (element instanceof PsiClass cls && cls.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            if (cls.isAnnotationType()) {
                if (PsiMethod.class.equals(type) && notNull(cls.getExtendsList()) && Binis.isAnnotationInheritanceEnabled(element.getProject())) {
                    var methods = new ArrayList<PsiMethod>();
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
                                                .map(PsiElement::copy)
                                                .map(PsiMethod.class::cast)
                                                .forEach(m -> {
                                                    m.putUserData(AUGMENTED, m.getName());
                                                    methods.add(m);
                                                }));
                        result = (List) methods;
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
                            var fields = new ArrayList<PsiField>();
                            Arrays.stream(cls.getAllMethods())
                                    .filter(m -> !m.hasParameters())
                                    .filter(m -> nonNull(m.getReturnType()))
                                    .map(this::createField)
                                    .forEach(m -> {
                                        m.putUserData(AUGMENTED, m.getName());
                                        fields.add(m);
                                    });
                            result = (List) fields;
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
        }

        return (List) result;
    }

    protected PsiField createField(PsiMethod method) {
        var factory = JavaPsiFacade.getElementFactory(method.getProject());
        var field = factory.createField(method.getName(), method.getReturnType());
        field.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        return field;
    }
}
