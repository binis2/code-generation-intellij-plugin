package net.binis.intellij.util;

import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.annotation.type.GenerationStrategy;
import org.apache.commons.lang3.StringUtils;

import static java.util.Objects.nonNull;

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


}
