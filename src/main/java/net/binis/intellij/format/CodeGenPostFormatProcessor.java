package net.binis.intellij.format;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import net.binis.codegen.tools.Reflection;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

import static java.util.Objects.nonNull;

public class CodeGenPostFormatProcessor implements PostFormatProcessor {

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

                var lines = document.getText().split("\n");

                ApplicationManager.getApplication().runWriteAction(() -> {
                    PsiDocumentManager.getInstance(project).commitDocument(document);

                    var toIndent = false;
                    var brackets = 0;
                    for (var i = 0; i < lines.length; i++) {
                        var line = lines[i].trim();
                        if (!line.startsWith("//")) {
                            if (line.endsWith("._add()")) {
                                toIndent = true;
                            } else if (line.startsWith(".done()")) {
                                if (!toIndent) {
                                    int startOffset = document.getLineStartOffset(i);
                                    int endOffset = document.getLineEndOffset(i);
                                    String lineText = document.getText(new TextRange(startOffset, endOffset));

                                    String trimmed = lineText.substring(Math.min(indentSize, Reflection.invoke(indexOfNonWhitespace, lineText)));
                                    document.replaceString(startOffset, endOffset, trimmed);
                                }
                                if (brackets <= 0) {
                                    toIndent = false;
                                }
                            } else if (toIndent) {
                                if (!line.startsWith("._and()")) {
                                    int startOffset = document.getLineStartOffset(i);
                                    int endOffset = document.getLineEndOffset(i);
                                    String lineText = document.getText(new TextRange(startOffset, endOffset));

                                    String trimmed = " ".repeat(indentSize) + lineText;
                                    document.replaceString(startOffset, endOffset, trimmed);
                                }
                                brackets += StringUtils.countMatches(line, '{') - StringUtils.countMatches(line, '}');
                                if ((brackets == 0 && line.contains(";")) || (brackets < 0 && line.contains("}"))) {
                                    toIndent = false;
                                }
                            }
                        }
                    }
                });
            }
        }
        return range;
    }

    @Override
    public boolean isWhitespaceOnly() {
        return true;
    }
}
