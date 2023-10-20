package net.binis.intellij.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.update.UpdatedFilesListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import net.binis.intellij.listeners.CodeGenBulkFileListener;
import net.binis.intellij.listeners.CodeGenFileEditorManagerListener;

public class CodeGenProjectService {

    private static final Logger log = Logger.getInstance(CodeGenProjectService.class);

    public CodeGenProjectService(Project project) {
        log.info("Project (" + project.getName() + ") service started");
        project.getMessageBus().connect().subscribe(UpdatedFilesListener.UPDATED_FILES,
                (UpdatedFilesListener) files ->
                        files.forEach(file -> log.warn("File updated: " + file)));

        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new CodeGenFileEditorManagerListener());

        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new CodeGenBulkFileListener(project));
    }
}