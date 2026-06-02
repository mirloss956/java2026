package com.fileorganizer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;

/**
 * 負責人：C
 * 讀寫 config.json（放在使用者 home 目錄下的 .fileorganizer/）。
 */
public class ConfigLoader {

    private static final Path CONFIG_DIR  = Path.of(System.getProperty("user.home"), ".fileorganizer");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public AppConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            return new AppConfig(); // 第一次啟動，回傳預設值
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            return GSON.fromJson(reader, AppConfig.class);
        } catch (IOException e) {
            System.err.println("設定檔讀取失敗，使用預設值：" + e.getMessage());
            return new AppConfig();
        }
    }

    public void save(AppConfig config) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            System.err.println("設定檔寫入失敗：" + e.getMessage());
        }
    }
}
