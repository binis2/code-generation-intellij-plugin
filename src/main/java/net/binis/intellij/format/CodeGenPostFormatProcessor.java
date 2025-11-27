package net.binis.intellij.format;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.tools.Reflection;
import net.binis.intellij.CodeGenAnnotator;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.intellij.openapi.editor.Document;

import static java.util.Objects.nonNull;

public class CodeGenPostFormatProcessor implements PostFormatProcessor {

    private final Logger log = Logger.getInstance(CodeGenAnnotator.class);
    protected Method indexOfNonWhitespace = Reflection.findMethod("indexOfNonWhitespace", String.class);

    @Override
    public @NotNull PsiElement processElement(@NotNull PsiElement element, @NotNull CodeStyleSettings codeStyleSettings) {
        return element;
    }

    @Override
    public @NotNull TextRange processText(@NotNull PsiFile file, @NotNull TextRange range, @NotNull CodeStyleSettings settings) {
        if (range.getLength() == file.getTextLength()) {
            var project = file.getProject();
            var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (nonNull(document)) {
                var indentSize = settings.getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions().INDENT_SIZE;

                ApplicationManager.getApplication().runWriteAction(() -> {
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                    var exec = new ArrayList<Runnable>();
                    var indents = 1;
                    for (var i = 0; i < document.getLineCount(); i++) {
                        var startOffset = document.getLineStartOffset(i);
                        var endOffset = document.getLineEndOffset(i);
                        var lineText = document.getText(new TextRange(startOffset, endOffset));

                        var firstElement = file.findElementAt(startOffset + (int) Reflection.invoke(indexOfNonWhitespace, lineText));

                        if (firstElement instanceof PsiJavaToken token && ".".equals(token.getText()) && token.getParent() instanceof PsiReferenceExpression expression) {
                            var call = PsiTreeUtil.findChildOfType(expression, PsiMethodCallExpression.class);
                            if (nonNull(call)) {
                                var type = call.getType();
                                if (nonNull(type)) {
                                    var params = -1;
                                    if (expression.getNextSibling() instanceof PsiExpressionList list) {
                                        params = list.getExpressionCount();
                                        if (list.getParent() instanceof PsiMethodCallExpression actual && nonNull(actual.getType())) {
                                            type = actual.getType();
                                        }
                                    }
                                    var name = expression.getReferenceName();
                                    var typeName = type.getPresentableText();
                                    var idx = typeName.indexOf('<');
                                    if (idx > -1) {
                                        typeName = typeName.substring(0, idx);
                                    }

                                    if ("save".equals(name) || ("done".equals(name) || "select".equals(name) || "where".equals(name) && !typeName.contains("Modify")) && params == 0) {
                                        var line = i;
                                        if (indents > 1) {
                                            indents--;
                                        }
                                        if (indents > 1) {
                                            var ind = indents;
                                            exec.add(() -> toIndent(document, line, indentSize * ind));
                                        }
                                        exec.add(() -> toIndentBack(document, line, indentSize));
                                    } else if ((typeName.contains("EmbeddedCollectionModify") && ("_add".equals(name) || "_get".equals(name) || "_insert".equals(name) || "_first".equals(name) || "_last".equals(name)))) {
                                        var line = i;
                                        var ind = indents;
                                        exec.add(() -> toIndent(document, line, indentSize * ind));
                                        indents++;
                                    } else if ((typeName.contains("EmbeddedCodeCollection") || typeName.contains("EmbeddedSoloModify") || typeName.contains("EmbeddedCollectionModify") || typeName.contains("CodeMap") || typeName.contains("CodeList") || typeName.contains("CodeSet")) && params != 0) {
                                        var line = i;
                                        var ind = indents;
                                        exec.add(() -> toIndent(document, line, indentSize * ind));
                                    } else {
                                        if (indents > 1) {
                                            indents--;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    exec.forEach(Runnable::run);
                });
            }
        }
        return range;
    }

    private void toIndent(Document document, int line, int indentSize) {
        int startOffset = document.getLineStartOffset(line);
        int endOffset = document.getLineEndOffset(line);
        String lineText = document.getText(new TextRange(startOffset, endOffset));

        String trimmed = " ".repeat(indentSize) + lineText;
        document.replaceString(startOffset, endOffset, trimmed);
    }

    private void toIndentBack(Document document, int line, int indentSize) {
        int startOffset = document.getLineStartOffset(line);
        int endOffset = document.getLineEndOffset(line);
        String lineText = document.getText(new TextRange(startOffset, endOffset));

        String trimmed = lineText.substring(Math.min(indentSize, Reflection.invoke(indexOfNonWhitespace, lineText)));
        document.replaceString(startOffset, endOffset, trimmed);
    }


    @Override
    public boolean isWhitespaceOnly() {
        return true;
    }
}
