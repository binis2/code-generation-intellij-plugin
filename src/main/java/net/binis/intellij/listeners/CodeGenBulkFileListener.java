package net.binis.intellij.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import net.binis.codegen.tools.Holder;
import net.binis.intellij.tools.Lookup;

import java.util.HashSet;
import java.util.List;

public class CodeGenBulkFileListener implements BulkFileListener {

    private static final Logger log = Logger.getInstance(CodeGenBulkFileListener.class);

    @Override
    public void after(List<? extends VFileEvent> events) {
        var modules = new HashSet<Module>();
        events.stream()
                .filter(VFileEvent::isFromSave)
                .forEach(e ->
                        modules.addAll(Lookup.refreshCache(e.getFile())));

        modules.forEach(Lookup::rebuildModule);
    }
}
