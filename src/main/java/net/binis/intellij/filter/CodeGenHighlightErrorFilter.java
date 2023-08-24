package net.binis.intellij.filter;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.intellij.tools.Binis;
import net.binis.intellij.tools.Lookup;
import net.binis.intellij.tools.objects.EnricherData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class CodeGenHighlightErrorFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (nonNull(highlightInfo.getDescription()) && file instanceof PsiJavaFile java && Binis.isPluginEnabled((java.getProject()))) {
            var cls = PsiTreeUtil.findChildOfType(java, PsiClass.class);

            if (nonNull(cls)) {
                var data = Lookup.getPrototypeData(cls);
                if (nonNull(data)) {
                    var enrichers = (List<EnricherData>) data.getCustom().get("enrichers");
                    if (nonNull(enrichers)) {
                        for (var enricher : enrichers) {
                            if (nonNull(enricher.getParamsSuppresses()) && nonNull(enricher.getFilter())) {
                                for (var par : enricher.getFilter().apply(cls).toList()) {
                                    var suppress = highlightInfo.getDescription().equals(enricher.getParamsSuppresses()
                                            .params(Map.of(
                                            "name", par.getName(),
                                            "type", par.getType().getCanonicalText()))
                                            .interpolate());
                                    if (suppress) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return isNull(cls) || !cls.isAnnotationType() || !"@interface may not have extends list".equals(highlightInfo.getDescription());
        }
        return true;
    }
}
