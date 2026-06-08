package com.fileorganizer.service.impl;

import com.fileorganizer.model.DiskStats;
import com.fileorganizer.model.DiskStats.CategoryStats;
import com.fileorganizer.model.FolderStats;
import com.fileorganizer.service.DiskAnalysisService;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiskAnalysisServiceImpl implements DiskAnalysisService {

    private static final Map<String, String> EXT_MAP = Map.ofEntries(
        Map.entry("jpg",  "圖片"), Map.entry("jpeg", "圖片"),
        Map.entry("png",  "圖片"), Map.entry("gif",  "圖片"),
        Map.entry("webp", "圖片"), Map.entry("heic", "圖片"),
        Map.entry("mp4",  "影片"), Map.entry("mov",  "影片"),
        Map.entry("avi",  "影片"), Map.entry("mkv",  "影片"),
        Map.entry("mp3",  "音樂"), Map.entry("wav",  "音樂"),
        Map.entry("flac", "音樂"), Map.entry("aac",  "音樂"),
        Map.entry("pdf",  "文件"), Map.entry("docx", "文件"),
        Map.entry("xlsx", "文件"), Map.entry("pptx", "文件"),
        Map.entry("txt",  "文件"),
        Map.entry("java", "程式碼"), Map.entry("py", "程式碼"),
        Map.entry("js",   "程式碼"), Map.entry("ts", "程式碼"),
        Map.entry("zip",  "壓縮檔"), Map.entry("rar", "壓縮檔"),
        Map.entry("7z",   "壓縮檔"), Map.entry("tar", "壓縮檔")
    );

    private static final Map<String, String> COLOR_MAP = Map.of(
        "圖片",   "#378ADD",
        "影片",   "#D85A30",
        "音樂",   "#1D9E75",
        "文件",   "#7F77DD",
        "程式碼", "#BA7517",
        "壓縮檔", "#D4537E",
        "其他",   "#888780"
    );

    private final ExecutorService executor = Executors.newWorkStealingPool();

    @Override
    public CompletableFuture<DiskStats> analyze(Path root, Consumer<Long> onProgress) {
        return CompletableFuture.supplyAsync(() -> {

            Map<String, Long>    catBytes = new ConcurrentHashMap<>();
            Map<String, Integer> catCount = new ConcurrentHashMap<>();
            AtomicLong totalBytes = new AtomicLong();
            AtomicLong totalCount = new AtomicLong();

            try {
                Files.walk(root)
                     .parallel()
                     .filter(Files::isRegularFile)
                     .forEach(file -> {
                         try {
                             long size = Files.size(file);
                             String cat = classify(file);
                             totalBytes.addAndGet(size);
                             catBytes.merge(cat, size, Long::sum);
                             catCount.merge(cat, 1, Integer::sum);
                             long n = totalCount.incrementAndGet();
                             if (n % 200 == 0) onProgress.accept(n);
                         } catch (IOException ignored) {}
                     });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            List<CategoryStats> stats = catBytes.entrySet().stream()
                .map(e -> new CategoryStats(
                    e.getKey(),
                    COLOR_MAP.getOrDefault(e.getKey(), "#888780"),
                    e.getValue(),
                    catCount.getOrDefault(e.getKey(), 0)
                ))
                .sorted(Comparator.comparingLong(CategoryStats::bytes).reversed())
                .collect(Collectors.toList());

            return new DiskStats(totalBytes.get(), totalCount.get(), stats);

        }, executor);
    }

    @Override
    public CompletableFuture<FolderStats> buildFolderTree(Path root) {
        return CompletableFuture.supplyAsync(() -> buildNode(root), executor);
    }

    private FolderStats buildNode(Path dir) {
        List<FolderStats> children = new ArrayList<>();
        AtomicLong dirBytes = new AtomicLong();
        AtomicInteger dirCount = new AtomicInteger();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    FolderStats child = buildNode(entry);
                    children.add(child);
                    dirBytes.addAndGet(child.bytes());
                    dirCount.addAndGet(child.fileCount());
                } else if (Files.isRegularFile(entry)) {
                    try {
                        dirBytes.addAndGet(Files.size(entry));
                        dirCount.incrementAndGet();
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}

        children.sort(Comparator.comparingLong(FolderStats::bytes).reversed());
        return new FolderStats(dir, dirBytes.get(), dirCount.get(), children);
    }

    private static String classify(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "其他";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXT_MAP.getOrDefault(ext, "其他");
    }
}