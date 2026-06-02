package com.fileorganizer.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 應用程式設定（對應 config.json）。
 * C 負責讀寫，A 在設定頁面修改，B 和 RuleEngine 讀取使用。
 */
public class AppConfig {

    private Path sourceDirectory;     // 要整理的來源資料夾
    private Path targetDirectory;     // 整理後的目標資料夾
    private boolean watchEnabled;     // 是否啟用即時監控
    private boolean dryRunDefault;    // 預設是否為預覽模式
    private boolean detectDuplicates; // 是否偵測重複檔案
    private String activeRuleMode;    // "extension" | "date" | "custom"
    private List<String> customRules; // 使用者自訂規則（序列化後的 JSON）

    public AppConfig() {
        this.watchEnabled = false;
        this.dryRunDefault = true;
        this.detectDuplicates = true;
        this.activeRuleMode = "extension";
        this.customRules = new ArrayList<>();
    }

    // --- Getters / Setters ---
    public Path getSourceDirectory() { return sourceDirectory; }
    public void setSourceDirectory(Path sourceDirectory) { this.sourceDirectory = sourceDirectory; }

    public Path getTargetDirectory() { return targetDirectory; }
    public void setTargetDirectory(Path targetDirectory) { this.targetDirectory = targetDirectory; }

    public boolean isWatchEnabled() { return watchEnabled; }
    public void setWatchEnabled(boolean watchEnabled) { this.watchEnabled = watchEnabled; }

    public boolean isDryRunDefault() { return dryRunDefault; }
    public void setDryRunDefault(boolean dryRunDefault) { this.dryRunDefault = dryRunDefault; }

    public boolean isDetectDuplicates() { return detectDuplicates; }
    public void setDetectDuplicates(boolean detectDuplicates) { this.detectDuplicates = detectDuplicates; }

    public String getActiveRuleMode() { return activeRuleMode; }
    public void setActiveRuleMode(String activeRuleMode) { this.activeRuleMode = activeRuleMode; }

    public List<String> getCustomRules() { return customRules; }
    public void setCustomRules(List<String> customRules) { this.customRules = customRules; }
}
