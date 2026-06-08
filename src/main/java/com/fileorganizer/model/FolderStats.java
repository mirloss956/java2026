package com.fileorganizer.model;

import java.nio.file.Path;
import java.util.List;

public record FolderStats(
    Path path,
    long bytes,
    int fileCount,
    List<FolderStats> children
) {
    public String name() {
        return path.getFileName() != null
            ? path.getFileName().toString()
            : path.toString();
    }

    public String formattedSize() {
        return DiskStats.humanReadable(bytes);
    }
}