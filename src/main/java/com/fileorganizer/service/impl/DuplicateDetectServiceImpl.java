package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.service.DuplicateDetectService;
import com.fileorganizer.util.FileHashUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 負責人：B
 *
 * 修正：重複群組中所有檔案（包含第一個）都標記為 DUPLICATE，
 * 讓 OrganizerFacade 可以把整組一起移到「重複檔案」資料夾。
 */
public class DuplicateDetectServiceImpl implements DuplicateDetectService {

    @Override
    public Map<String, List<FileItem>> detectDuplicates(List<FileItem> items) {
        Map<String, List<FileItem>> allGroups = new HashMap<>();

        if (items == null || items.isEmpty()) {
            return new HashMap<>();
        }

        // 1. 計算 checksum 並分組
        for (FileItem item : items) {
            if (item.getChecksum() == null || item.getChecksum().isEmpty()) {
                try {
                    String md5 = FileHashUtil.md5(item.getSourcePath());
                    item.setChecksum(md5);
                } catch (Exception e) {
                    System.err.println("計算檔案雜湊值失敗: " + item.getFileName()
                            + " -> " + e.getMessage());
                    continue;
                }
            }

            String checksum = item.getChecksum();
            if (checksum != null) {
                allGroups.computeIfAbsent(checksum, k -> new ArrayList<>()).add(item);
            }
        }

        // 2. 篩出重複群組，整組（包含第一個）都標記為 DUPLICATE
        Map<String, List<FileItem>> duplicateGroups = new HashMap<>();

        for (Map.Entry<String, List<FileItem>> entry : allGroups.entrySet()) {
            List<FileItem> groupFiles = entry.getValue();
            if (groupFiles.size() > 1) {
                duplicateGroups.put(entry.getKey(), groupFiles);
                // 全部標記為 DUPLICATE，包含第一個
                for (FileItem file : groupFiles) {
                    file.setStatus(FileStatus.DUPLICATE);
                }
            }
        }

        return duplicateGroups;
    }
}
