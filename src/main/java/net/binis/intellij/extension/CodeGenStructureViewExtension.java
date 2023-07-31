package net.binis.intellij.extension;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import net.binis.intellij.tools.Binis;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.nonNull;

public class CodeGenStructureViewExtension implements StructureViewExtension {
    @Override
    public Class<? extends PsiElement> getType() {
        return PsiClass.class;
    }

    @Override
    public StructureViewTreeElement[] getChildren(PsiElement parent) {
        final PsiClass cls = (PsiClass) parent;

        if (cls.isAnnotationType() && nonNull(cls.getExtendsList()) && Binis.isPluginEnabled(cls.getProject())) {
            var list = cls.getExtendsList().getReferencedTypes();
        }

        return new StructureViewTreeElement[0];
    }

    @Nullable
    @Override
    public Object getCurrentEditorElement(Editor editor, PsiElement parent) {
        return null;
    }
}
