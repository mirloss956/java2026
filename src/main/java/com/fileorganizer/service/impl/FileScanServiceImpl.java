package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.FileScanService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 負責人：B
 * 實作 FileScanService 介面
 */
public class FileScanServiceImpl implements FileScanService {

    /**
     * 掃描資料夾（不含子資料夾）
     */
    @Override
    public List<FileItem> scan(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return new ArrayList<>();
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile) // 只抓檔案，不抓子資料夾
                .map(this::createFileItem)    // 呼叫輔助方法轉成 FileItem
                .filter(Objects::nonNull)     // 過濾掉轉換失敗的
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("掃描資料夾失敗: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 掃描資料夾（含子資料夾，限制深度為 depth 層）
     */
    @Override
    public List<FileItem> scanRecursive(Path directory, int depth) {
        if (directory == null || !Files.isDirectory(directory)) {
            return new ArrayList<>();
        }

        try (Stream<Path> stream = Files.walk(directory, depth)) {
            return stream
                .filter(Files::isRegularFile)
                .map(this::createFileItem)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("遞迴掃描資料夾失敗: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 輔助方法：讀取 Path 的屬性並建立 FileItem 物件
     */
    private FileItem createFileItem(Path path) {
        try {
            // 使用 BasicFileAttributes 效率最高，一次讀取大小跟時間
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            long sizeBytes = attrs.size();
            
            // 將 FileTime 轉換為 LocalDateTime
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), 
                ZoneId.systemDefault()
            );
            
            return new FileItem(path, sizeBytes, lastModified);
            
        } catch (IOException e) {
            System.err.println("無法讀取檔案屬性: " + path + " -> " + e.getMessage());
            return null; // 讀取失敗則回傳 null，後續會被過濾掉
        }
    }
}