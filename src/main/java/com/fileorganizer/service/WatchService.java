package com.fileorganizer.service;

import java.nio.file.Path;

/**
 * 負責人：C
 * 監控資料夾變動，有新檔案進來時自動觸發整理。
 */
public interface WatchService {

    /** 開始監控指定資料夾（在背景執行緒執行） */
    void startWatch(Path directory);

    /** 停止監控 */
    void stopWatch();

    /** 是否正在監控中 */
    boolean isWatching();

    /**
     * 設定新檔案出現時的回呼（供 A 在 UI 上顯示通知）
     * 回呼會在 JavaFX Application Thread 上執行（已包 Platform.runLater）
     */
    void setOnNewFileDetected(java.util.function.Consumer<Path> callback);
}
