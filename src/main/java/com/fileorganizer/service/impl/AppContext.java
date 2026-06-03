package com.fileorganizer.service.impl;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.config.ConfigLoader;
import com.fileorganizer.rule.*;

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
 */
public class AppContext {

    private static AppContext instance;

    private final AppConfig config;
    private final ConfigLoader configLoader;   // ✅ 持有，供 saveConfig() 使用
    private final OrganizerFacade facade;

    private AppContext() {
        this.configLoader = new ConfigLoader();
        this.config = configLoader.load();

        // --- 規則引擎：C 組裝 ---
        List<Rule> rules = buildRules(config);
        RuleEngine ruleEngine = config.getTargetDirectory() != null
                ? new RuleEngine(rules, config.getTargetDirectory())
                : new RuleEngine(rules, java.nio.file.Path.of(System.getProperty("user.home"), "整理結果"));

        // --- Service 組裝：B 的三個 impl + config 注入 facade ---
        this.facade = new OrganizerFacade(
                new FileScanServiceImpl(),
                new FileMoveServiceImpl(),
                new DuplicateDetectServiceImpl(),
                new LogServiceImpl(),
                new FolderWatchServiceImpl(),
                ruleEngine,
                config   // ✅ 注入 config，讓 facade 讀取 detectDuplicates 開關
        );
    }

    private static List<Rule> buildRules(AppConfig config) {
        return switch (config.getActiveRuleMode()) {
            case "date"   -> List.of(new DateRule());
            case "custom" -> List.of(new DateRule(), new ExtensionRule());
            default       -> List.of(new ExtensionRule());
        };
    }

    public static void init() {
        instance = new AppContext();
    }

    public static AppContext get() {
        if (instance == null) throw new IllegalStateException("AppContext 尚未初始化，請先呼叫 init()");
        return instance;
    }

    public OrganizerFacade getFacade() { return facade; }
    public AppConfig getConfig()       { return config; }

    /**
     * ✅ 新增：將目前 config 寫回磁碟。
     * A 在設定頁面修改 AppConfig 後呼叫此方法即可持久化。
     * 用法：AppContext.get().saveConfig();
     */
    public void saveConfig() {
        configLoader.save(config);
    }
}
