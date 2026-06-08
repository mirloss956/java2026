// service/DiskAnalysisService.java
package com.fileorganizer.service;

import com.fileorganizer.model.DiskStats;
import com.fileorganizer.model.FolderStats;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface DiskAnalysisService {

    // 非同步掃描，onProgress 每 200 個檔案回報一次給 UI 顯示進度
    CompletableFuture<DiskStats> analyze(Path root, Consumer<Long> onProgress);

    // 資料夾樹狀結構（給 TreeMap 用）
    CompletableFuture<FolderStats> buildFolderTree(Path root);
}