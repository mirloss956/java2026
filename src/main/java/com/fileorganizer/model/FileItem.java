package com.fileorganizer.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 代表一個被掃描到的檔案。
 * A（UI）用它顯示列表，B（Service）用它做搬移，C（Rule）用它做分類判斷。
 */
public class FileItem {

    private final Path sourcePath;
    private Path destinationPath;        // 整理後的目標路徑（由 RuleEngine 決定）
    private final long sizeBytes;
    private final LocalDateTime lastModified;
    private final String extension;      // 小寫，例如 "jpg"
    private FileStatus status;
    private String checksum;             // MD5，重複偵測用，延遲計算

    public FileItem(Path sourcePath, long sizeBytes, LocalDateTime lastModified) {
        this.sourcePath = sourcePath;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
        this.extension = extractExtension(sourcePath.getFileName().toString());
        this.status = FileStatus.PENDING;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }

    public Path getSourcePath() { return sourcePath; }
    public Path getDestinationPath() { return destinationPath; }
    public long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getLastModified() { return lastModified; }
    public String getExtension() { return extension; }
    public FileStatus getStatus() { return status; }
    public String getChecksum() { return checksum; }
    public String getFileName() { return sourcePath.getFileName().toString(); }

    public void setDestinationPath(Path destinationPath) { this.destinationPath = destinationPath; }
    public void setStatus(FileStatus status) { this.status = status; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    @Override
    public String toString() {
        return String.format("FileItem{name='%s', ext='%s', status=%s}", getFileName(), extension, status);
    }
}
