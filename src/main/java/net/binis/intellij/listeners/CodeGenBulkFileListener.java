package net.binis.intellij.listeners;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.projectWizard.NewProjectWizardConstants;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import net.binis.codegen.tools.Holder;
import net.binis.intellij.tools.Lookup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static java.util.Objects.nonNull;

public class CodeGenBulkFileListener implements BulkFileListener {

    private static final Logger log = Logger.getInstance(CodeGenBulkFileListener.class);
    private final Project project;

    public CodeGenBulkFileListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(List<? extends VFileEvent> events) {
        try {
            var modules = new HashSet<Module>();
            events.stream()
                    .filter(VFileEvent::isFromSave)
                    .forEach(e -> {
                        if (nonNull(e.getFile()) && JavaFileType.INSTANCE.equals(e.getFile().getFileType())) {
                            modules.addAll(Lookup.refreshCache(project, e.getFile()));
                        }
                    });

            //TODO: Find a way to determine if the module is dirty
            //modules.forEach(Lookup::rebuildModule);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to process file events", e);
        }
    }
}
