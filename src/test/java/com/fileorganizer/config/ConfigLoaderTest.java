package com.fileorganizer.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 負責人：C
 * ConfigLoader 單元測試。
 *
 * 測試重點：
 *  1. Path 序列化往返（save → load 結果完全一致）
 *  2. 首次啟動（config.json 不存在）回傳預設值
 *  3. JSON 損毀 / 空白檔案 → 回傳預設值，不 crash
 *  4. null Path 欄位存讀不 crash
 *  5. boolean 欄位正確往返
 *  6. 覆寫存檔（多次 save 取最新）
 */
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private ConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ConfigLoader(tempDir);
    }

    // ── 1. Path 序列化往返 ──────────────────────────────────────

    @Test
    void saveAndLoad_pathFieldsRoundTrip() {
        AppConfig cfg = new AppConfig();
        cfg.setSourceDirectory(Path.of("/Users/alice/Downloads"));
        cfg.setTargetDirectory(Path.of("/Users/alice/Organized"));

        loader.save(cfg);
        AppConfig loaded = loader.load();

        assertEquals(cfg.getSourceDirectory(), loaded.getSourceDirectory(),
                "sourceDirectory 序列化後應還原為相同 Path");
        assertEquals(cfg.getTargetDirectory(), loaded.getTargetDirectory(),
                "targetDirectory 序列化後應還原為相同 Path");
    }

    // ── 2. 首次啟動 ─────────────────────────────────────────────

    @Test
    void load_whenFileNotExist_returnsDefaults() {
        AppConfig cfg = loader.load();

        assertNotNull(cfg);
        assertNull(cfg.getSourceDirectory(),  "首次啟動 sourceDirectory 應為 null");
        assertNull(cfg.getTargetDirectory(),  "首次啟動 targetDirectory 應為 null");
        assertFalse(cfg.isWatchEnabled(),     "預設 watchEnabled = false");
        assertTrue(cfg.isDryRunDefault(),     "預設 dryRunDefault = true");
        assertTrue(cfg.isDetectDuplicates(),  "預設 detectDuplicates = true");
        assertEquals("extension", cfg.getActiveRuleMode(), "預設 activeRuleMode = extension");
    }

    // ── 3. JSON 損毀 ─────────────────────────────────────────────

    @Test
    void load_whenFileIsCorrupted_returnsDefaults() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{ this is not valid json !!!");

        AppConfig cfg = loader.load();

        assertNotNull(cfg, "JSON 損毀時應回傳預設值，不應 crash");
    }

    @Test
    void load_whenFileIsBlank_returnsDefaults() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "   ");

        AppConfig cfg = loader.load();

        assertNotNull(cfg, "空白 JSON 時應回傳預設值，不應 crash");
    }

    // ── 4. null Path 欄位 ────────────────────────────────────────

    @Test
    void saveAndLoad_nullPathFields_doNotThrow() {
        AppConfig cfg = new AppConfig();
        // sourceDirectory / targetDirectory 保持 null（預設值）

        assertDoesNotThrow(() -> loader.save(cfg),  "null Path 存檔時不應拋出例外");
        assertDoesNotThrow(() -> loader.load(),      "含 null Path 的 JSON 讀取時不應拋出例外");

        AppConfig loaded = loader.load();
        assertNull(loaded.getSourceDirectory());
        assertNull(loaded.getTargetDirectory());
    }

    // ── 5. boolean 欄位往返 ──────────────────────────────────────

    @Test
    void saveAndLoad_booleanFields_roundTrip() {
        AppConfig cfg = new AppConfig();
        cfg.setWatchEnabled(true);
        cfg.setDryRunDefault(false);
        cfg.setDetectDuplicates(false);
        cfg.setActiveRuleMode("date");

        loader.save(cfg);
        AppConfig loaded = loader.load();

        assertTrue(loaded.isWatchEnabled());
        assertFalse(loaded.isDryRunDefault());
        assertFalse(loaded.isDetectDuplicates());
        assertEquals("date", loaded.getActiveRuleMode());
    }

    // ── 6. 覆寫存檔（多次 save 取最新）────────────────────────────

    @Test
    void save_twice_latestValueWins() {
        AppConfig first = new AppConfig();
        first.setActiveRuleMode("date");
        loader.save(first);

        AppConfig second = new AppConfig();
        second.setActiveRuleMode("custom");
        loader.save(second);

        AppConfig loaded = loader.load();
        assertEquals("custom", loaded.getActiveRuleMode(), "第二次 save 應覆蓋第一次");
    }
}
