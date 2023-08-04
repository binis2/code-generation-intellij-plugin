package net.binis.intellij.tools.objects;

import com.intellij.psi.PsiClass;
import lombok.Builder;
import lombok.Data;
import net.binis.codegen.annotation.augment.AugmentTargetType;
import net.binis.codegen.annotation.augment.AugmentTargetTypeSeverity;
import net.binis.codegen.annotation.augment.AugmentType;
import net.binis.codegen.annotation.augment.CodeAugmentParameters;

import java.lang.reflect.Modifier;

@Data
@Builder
public class EnricherData {

    protected PsiClass cls;
    protected AugmentType adds;
    @Builder.Default
    protected AugmentTargetType targets = AugmentTargetType.EVERYTHING;
    @Builder.Default
    protected AugmentTargetTypeSeverity severity = AugmentTargetTypeSeverity.ERROR;
    @Builder.Default
    protected String name = "";
    @Builder.Default
    protected String type = "";
    @Builder.Default
    protected long modifier = Modifier.PUBLIC;
    protected CodeAugmentParameters parameters;


}
