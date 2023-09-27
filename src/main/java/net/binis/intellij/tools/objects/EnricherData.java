package net.binis.intellij.tools.objects;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import lombok.Builder;
import lombok.Data;
import net.binis.codegen.annotation.augment.AugmentTargetType;
import net.binis.codegen.annotation.augment.AugmentTargetTypeSeverity;
import net.binis.codegen.annotation.augment.AugmentType;
import net.binis.codegen.annotation.augment.CodeAugmentParameters;
import net.binis.codegen.tools.Interpolator;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

@Data
@Builder
public class EnricherData {

    protected PsiClass cls;
    protected AugmentType adds;
    @Builder.Default
    protected List<AugmentTargetType> targets = List.of(AugmentTargetType.EVERYTHING);
    @Builder.Default
    protected AugmentTargetTypeSeverity severity = AugmentTargetTypeSeverity.ERROR;
    @Builder.Default
    protected String name = "";
    @Builder.Default
    protected String type = "";
    @Builder.Default
    protected long modifier = Modifier.PUBLIC;
    protected CodeAugmentParameters parameters;
    protected Function<PsiClass, Stream<PsiParameter>> filter;
    protected Interpolator suppresses;
    protected Interpolator paramsSuppresses;

}
