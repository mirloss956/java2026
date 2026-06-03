package com.fileorganizer.service.impl;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.config.ConfigLoader;
import com.fileorganizer.rule.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 負責人：C
 *
 * 應用程式的組裝中心（簡易 DI）。
 * JavaFX App 啟動時呼叫 AppContext.init()，
 * 之後所有 Controller 透過 AppContext.get().getFacade() 取得 facade。
 *
 * 修正：
 *  - 持有 ConfigLoader 實例，暴露 saveConfig() 供 A 存檔
 *  - 將 config 注入 OrganizerFacade，讓 facade 能讀取 detectDuplicates 開關
 *  - 新增 updateRuleMode(String)，支援執行期切換規則不重啟應用
 */
public class AppContext {

    private static AppContext instance;

    private final AppConfig config;
    private final ConfigLoader configLoader;
    private final OrganizerFacade facade;

    private AppContext() {
        this.configLoader = new ConfigLoader();
        this.config = configLoader.load();

        Path targetRoot = config.getTargetDirectory() != null
                ? config.getTargetDirectory()
                : Path.of(System.getProperty("user.home"), "整理結果");

        RuleEngine ruleEngine = new RuleEngine(buildRules(config), targetRoot);

        this.facade = new OrganizerFacade(
                new FileScanServiceImpl(),
                new FileMoveServiceImpl(),
                new DuplicateDetectServiceImpl(),
                new LogServiceImpl(),
                new FolderWatchServiceImpl(),
                ruleEngine,
                config
        );
    }

    // =========================================================
    // 執行期切換規則（⑤）
    // =========================================================

    /**
     * 切換規則模式並立即生效，不需重啟應用程式。
     * A 在設定頁面切換 RadioButton 後呼叫此方法。
     *
     * 用法：AppContext.get().updateRuleMode("date");
     *
     * @param mode "extension" | "date" | "custom"
     */
    public void updateRuleMode(String mode) {
        config.setActiveRuleMode(mode);

        Path targetRoot = config.getTargetDirectory() != null
                ? config.getTargetDirectory()
                : Path.of(System.getProperty("user.home"), "整理結果");

        RuleEngine newEngine = new RuleEngine(buildRules(config), targetRoot);
        facade.setRuleEngine(newEngine);
    }

    // =========================================================
    // 設定存檔
    // =========================================================

    /** 將目前 config 寫回磁碟。A 修改設定後呼叫此方法持久化。 */
    public void saveConfig() {
        configLoader.save(config);
    }

    // =========================================================
    // 靜態入口
    // =========================================================

    public static void init() {
        instance = new AppContext();
    }

    public static AppContext get() {
        if (instance == null) throw new IllegalStateException("AppContext 尚未初始化，請先呼叫 init()");
        return instance;
    }

    public OrganizerFacade getFacade() { return facade; }
    public AppConfig getConfig()       { return config; }

    // =========================================================
    // 私有工具
    // =========================================================

    private static List<Rule> buildRules(AppConfig config) {
        return switch (config.getActiveRuleMode()) {
            case "date"   -> List.of(new DateRule());
            case "custom" -> List.of(new DateRule(), new ExtensionRule());
            default       -> List.of(new ExtensionRule());
        };
    }
}
