package net.binis.intellij.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;

public class CodeGenFileEditorManagerListener implements FileEditorManagerListener {

    private static final Logger log = Logger.getInstance(CodeGenFileEditorManagerListener.class);

    @Override
    public void fileClosed(FileEditorManager source, VirtualFile file) {
        log.info("File closed: " + file.getName());
    }

}