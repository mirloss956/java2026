package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.service.DuplicateDetectService;
import com.fileorganizer.util.FileHashUtil; // 引入 C 同學寫的工具

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 負責人：B
 * 實作 DuplicateDetectService 介面
 */
public class DuplicateDetectServiceImpl implements DuplicateDetectService {

    @Override
    public Map<String, List<FileItem>> detectDuplicates(List<FileItem> items) {
        // 用來暫存 所有 checksum 對應的檔案清單（包含沒重複的）
        Map<String, List<FileItem>> allGroups = new HashMap<>();

        if (items == null || items.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 遍歷所有檔案，計算雜湊值並分組
        for (FileItem item : items) {
            // 如果這個檔案之前已經算過雜湊值，就不用重複計算（延遲計算優化）
            if (item.getChecksum() == null || item.getChecksum().isEmpty()) {
                try {
                    // 呼叫 C 同學的工具類別計算 MD5
                    // ⚠️ 注意：若 FileHashUtil 的方法名不同（例如叫 calculate），請自行修正
                    String md5 = FileHashUtil.calculateMD5(item.getSourcePath());
                    item.setChecksum(md5);
                } catch (Exception e) {
                    System.err.println("計算檔案雜湊值失敗: " + item.getFileName() + " -> " + e.getMessage());
                    continue; // 計算失敗就跳過這個檔案
                }
            }

            String checksum = item.getChecksum();
            if (checksum != null) {
                // 將相同 checksum 的檔案歸類到同一個 List 裡
                allGroups.computeIfAbsent(checksum, k -> new ArrayList<>()).add(item);
            }
        }

        // 用來存放「真正有重複」的群組結果
        Map<String, List<FileItem>> duplicateGroups = new HashMap<>();

        // 2. 檢查分組結果，篩選出數量 > 1 的群組，並標記狀態
        for (Map.Entry<String, List<FileItem>> entry : allGroups.entrySet()) {
            List<FileItem> groupFiles = entry.getValue();

            if (groupFiles.size() > 1) {
                // 有重複！將這一組放入回傳的 Map 中
                duplicateGroups.put(entry.getKey(), groupFiles);

                // 除了第一個檔案保持原樣（或是 PENDING），其餘的都標記為 DUPLICATE
                for (int i = 1; i < groupFiles.size(); i++) {
                    groupFiles.get(i).setStatus(FileStatus.DUPLICATE);
                }
            }
        }

        return duplicateGroups;
    }
}