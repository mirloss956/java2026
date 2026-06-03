package com.fileorganizer.service.impl;

import com.fileorganizer.service.WatchService;
import javafx.application.Platform;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 負責人：C
 * WatchService 實作。用背景執行緒監控資料夾，
 * 有新檔案時透過 Platform.runLater() 通知 A 的 UI callback。
 *
 * 修正（⑦）：
 *  - stopWatch() 補上 executor.shutdownNow()，防止執行緒殘留
 *  - 新增 shutdown()，供 App.stop() 呼叫做完整資源清理
 */
public class FolderWatchServiceImpl implements WatchService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "watch-service-thread");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean watching = false;
    private Consumer<Path> onNewFileDetected;
    private java.nio.file.WatchService nioWatcher;

    @Override
    public void startWatch(Path directory) {
        if (watching) return;
        watching = true;

        executor.submit(() -> {
            try {
                nioWatcher = FileSystems.getDefault().newWatchService();
                directory.register(nioWatcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (watching && !Thread.currentThread().isInterrupted()) {
                    WatchKey key = nioWatcher.poll(500, TimeUnit.MILLISECONDS);
                    if (key == null) continue;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            Path newFile = directory.resolve((Path) event.context());
                            if (onNewFileDetected != null) {
                                Platform.runLater(() -> onNewFileDetected.accept(newFile));
                            }
                        }
                    }

                    if (!key.reset()) break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("WatchService 錯誤：" + e.getMessage());
            }
        });
    }

    @Override
    public void stopWatch() {
        watching = false;
        closeNioWatcher();
        // ✅ 修正：中斷並關閉執行緒池，防止執行緒殘留
        executor.shutdownNow();
    }

    /**
     * ✅ 新增：完整資源清理，供 App.stop() 在應用程式關閉時呼叫。
     * 與 stopWatch() 的差異：等待執行緒確實結束（最多 2 秒），
     * 確保 JVM 能乾淨退出。
     */
    public void shutdown() {
        watching = false;
        closeNioWatcher();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("WatchService 執行緒未能在時限內結束");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isWatching() { return watching; }

    @Override
    public void setOnNewFileDetected(Consumer<Path> callback) {
        this.onNewFileDetected = callback;
    }

    // ── 私有工具 ───────────────────────────────────────────────

    private void closeNioWatcher() {
        try {
            if (nioWatcher != null) {
                nioWatcher.close();
                nioWatcher = null;
            }
        } catch (Exception ignored) {}
    }
}
