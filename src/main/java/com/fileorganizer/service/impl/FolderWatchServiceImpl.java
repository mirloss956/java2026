package com.fileorganizer.service.impl;

import com.fileorganizer.service.WatchService;
import javafx.application.Platform;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 負責人：C
 *
 * WatchService 實作。用背景執行緒監控資料夾，
 * 有新檔案時透過 Platform.runLater() 通知 A 的 UI callback。
 *
 * 修正：shutdown() 加入 awaitTermination(2s)，
 * 確保 JVM 退出前執行緒確實結束，而非僅 shutdownNow() 就回傳。
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
        try {
            if (nioWatcher != null) nioWatcher.close();
        } catch (Exception ignored) {}
    }

    /**
     * 供 App.stop() 呼叫。
     * stopWatch() 令 watching = false 並關閉 nioWatcher，
     * 再 shutdownNow() + awaitTermination 等待執行緒確實結束，
     * 避免 JVM 在 daemon thread 未結束時強制退出造成資源洩漏。
     */
    public void shutdown() {
        stopWatch();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("WatchService 執行緒未能在 2 秒內結束");
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
}
