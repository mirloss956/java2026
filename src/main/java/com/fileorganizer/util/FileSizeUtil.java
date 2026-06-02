package com.fileorganizer.util;

/**
 * 顯示用：將 bytes 轉成人類可讀格式（KB / MB / GB）。
 * A（UI）和 log 輸出都可使用。
 */
public class FileSizeUtil {

    private FileSizeUtil() {}

    public static String humanReadable(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
