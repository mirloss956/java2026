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
 * 修正：
 *  - 註冊 Path TypeAdapter，避免 Gson 無法序列化 java.nio.file.Path（interface）
 *  - 提供雙建構子：預設用 user.home，測試用注入自訂目錄（避免污染真實設定）
 */
public class ConfigLoader {

    private static final String CONFIG_FILENAME = "config.json";

    private final Path configDir;
    private final Path configFile;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
            .create();

    /** 正式環境用：設定目錄固定為 ~/.fileorganizer/ */
    public ConfigLoader() {
        this(Path.of(System.getProperty("user.home"), ".fileorganizer"));
    }

    /** 測試用：可注入任意目錄（例如 @TempDir） */
    public ConfigLoader(Path configDir) {
        this.configDir  = configDir;
        this.configFile = configDir.resolve(CONFIG_FILENAME);
    }

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
        if (!Files.exists(configFile)) {
            return new AppConfig();
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            AppConfig cfg = GSON.fromJson(reader, AppConfig.class);
            return cfg != null ? cfg : new AppConfig();
        } catch (Exception e) {
            // IOException 或 JSON 損毀時，回傳預設值而非 crash
            System.err.println("設定檔讀取失敗，使用預設值：" + e.getMessage());
            return new AppConfig();
        }
    }

    public void save(AppConfig config) {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            System.err.println("設定檔寫入失敗：" + e.getMessage());
        }
    }
}
