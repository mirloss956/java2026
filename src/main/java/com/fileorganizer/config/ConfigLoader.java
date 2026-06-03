package com.fileorganizer.config;

import com.google.gson.*;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;

/**
 * 負責人：C
 * 讀寫 config.json（放在使用者 home 目錄下的 .fileorganizer/）。
 *
 * 修正：註冊 Path TypeAdapter，避免 Gson 無法序列化 java.nio.file.Path（interface）。
 */
public class ConfigLoader {

    private static final Path CONFIG_DIR  = Path.of(System.getProperty("user.home"), ".fileorganizer");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
            .create();

    // ── Path TypeAdapter ────────────────────────────────────────────────────
    private static class PathTypeAdapter implements JsonSerializer<Path>, JsonDeserializer<Path> {

        @Override
        public JsonElement serialize(Path src, Type typeOfSrc, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Path deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
                throws JsonParseException {
            String raw = json.getAsString();
            if (raw == null || raw.isBlank()) return null;
            return Path.of(raw);
        }
    }
    // ────────────────────────────────────────────────────────────────────────

    public AppConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            return new AppConfig(); // 第一次啟動，回傳預設值
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            AppConfig cfg = GSON.fromJson(reader, AppConfig.class);
            return cfg != null ? cfg : new AppConfig();
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
