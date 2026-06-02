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
                                // 切回 JavaFX UI 執行緒
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

    @Override
    public boolean isWatching() { return watching; }

    @Override
    public void setOnNewFileDetected(Consumer<Path> callback) {
        this.onNewFileDetected = callback;
    }
}
