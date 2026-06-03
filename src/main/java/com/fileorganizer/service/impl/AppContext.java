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
 * 之後所有 Controller 透過 AppContext.getFacade() 取得 facade。
 *
 * B 的 impl 實作好之後，在這裡替換 null 即可，A 完全不需要改程式碼。
 */
public class AppContext {

    private static AppContext instance;

    private final AppConfig config;
    private final OrganizerFacade facade;

    private AppContext() {
        ConfigLoader loader = new ConfigLoader();
        this.config = loader.load();

        // --- 規則引擎：C 組裝 ---
        List<Rule> rules = buildRules(config);
        RuleEngine ruleEngine = config.getTargetDirectory() != null
                ? new RuleEngine(rules, config.getTargetDirectory())
                : new RuleEngine(rules, java.nio.file.Path.of(System.getProperty("user.home"), "整理結果"));

        // --- Service 組裝：B 實作好後在這裡替換 ---
        // TODO（B）：把下面三個 null 換成對應的 impl
        //   new FileScanServiceImpl()
        //   new FileMoveServiceImpl()
        //   new DuplicateDetectServiceImpl()
        this.facade = new OrganizerFacade(
                null,   // FileScanService    ← B 實作後替換
                null,   // FileMoveService    ← B 實作後替換
                null,   // DuplicateDetect    ← B 實作後替換
                new LogServiceImpl(),
                new FolderWatchServiceImpl(),
                ruleEngine
        );
    }

    private static List<Rule> buildRules(AppConfig config) {
        return switch (config.getActiveRuleMode()) {
            case "date"   -> List.of(new DateRule());
            case "custom" -> List.of(new DateRule(), new ExtensionRule()); // 日後擴充
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
}
