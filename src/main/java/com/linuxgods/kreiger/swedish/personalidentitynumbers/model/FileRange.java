package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;

public class FileRange extends TextRanged<VirtualFile> {

    public FileRange(VirtualFile file, TextRange range) {
        super(file, range);
    }

    public VirtualFile getFile() {
        return getValue();
    }
}
