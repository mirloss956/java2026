```java
// model/DiskStats.java
package com.fileorganizer.model;

import java.util.List;

public record DiskStats(
    long totalBytes,
    long fileCount,
    List<CategoryStats> byCategory  // 給圓餅圖
) {
    // 每個類別的統計
    public record CategoryStats(
        String name,        // "圖片", "影片", "文件"...
        String hexColor,    // "#378ADD"
        long bytes,
        int count
    ) {
        public double percent(long total) {
            return total == 0 ? 0 : bytes * 100.0 / total;
        }

        public String formattedSize() {
            return DiskStats.humanReadable(bytes);
        }
    }

    public String formattedTotal() {
        return humanReadable(totalBytes);
    }

    public static String humanReadable(long bytes) {
        if (bytes >= 1_073_741_824) return "%.1f GB".formatted(bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576)     return "%.1f MB".formatted(bytes / 1_048_576.0);
        if (bytes >= 1_024)         return "%.1f KB".formatted(bytes / 1_024.0);
        return bytes + " B";
    }
}
```