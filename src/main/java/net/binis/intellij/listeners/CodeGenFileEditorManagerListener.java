package net.binis.intellij.listeners;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import net.binis.intellij.tools.Lookup;

public class CodeGenFileEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void fileClosed(FileEditorManager source, VirtualFile file) {
        Lookup.refreshCache(file);
    }

}