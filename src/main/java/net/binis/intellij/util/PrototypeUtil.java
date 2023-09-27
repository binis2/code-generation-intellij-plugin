package net.binis.intellij.util;

import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.annotation.type.GenerationStrategy;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.nonNull;
import static net.binis.codegen.tools.Tools.with;

public class PrototypeUtil {

    public static GenerationStrategy readPrototypeStrategy(PsiAnnotationMemberValue memberValue) {
        return readAnnotationEnumValue(memberValue, GenerationStrategy.class);
    }

    public static GenerationStrategy readPrototypeStrategy(PsiElement element) {
        var ann = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
        if (nonNull(ann)) {
            var value = ann.findAttributeValue("strategy");
            if (nonNull(value)) {
                return readPrototypeStrategy(value);
            }
        }
        return null;
    }

    public static <T extends Enum<T>> T readAnnotationEnumValue(Object memberValue, Class<T> type) {
        if (memberValue instanceof PsiReferenceExpression exp) {
            var value = exp.getReferenceName();
            if (StringUtils.isNotBlank(value)) {
                try {
                    return Enum.valueOf(type, value);
                } catch (Exception e) {
                    //ignore
                }
            }
        } else if (memberValue instanceof JvmAnnotationEnumFieldValue value) {
            try {
                return Enum.valueOf(type, value.getField().getName());
            } catch (Exception e) {
                //ignore
            }
        }

        return null;
    }

    public static <T extends Enum<T>> List<T> readAnnotationEnumListValue(Object memberValue, Class<T> type) {
        var list = new ArrayList<T>();
        if (memberValue instanceof JvmAnnotationArrayValue array) {
            array.getValues().forEach(v ->
                    with(readAnnotationEnumValue(v, type), list::add));
        }

        return list;
    }


    public static void readAnnotationConstantValue(JvmAnnotationAttributeValue value, Consumer<Object> consumer) {
        if (value instanceof JvmAnnotationConstantValue val) {
            with(val.getConstantValue(), consumer);
        }
    }
}
