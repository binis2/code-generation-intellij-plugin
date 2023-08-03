package net.binis.intellij.util;

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
        if (memberValue instanceof PsiReferenceExpression exp) {
            var value = exp.getReferenceName();
            if (StringUtils.isNotBlank(value)) {
                try {
                    return GenerationStrategy.valueOf(value);
                } catch (Exception e) {
                    //ignore
                }
            }
        }

        return null;
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

}
