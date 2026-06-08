# File Organizer

JavaFX 桌面應用程式，自動掃描並依規則整理資料夾中的檔案。支援副檔名分類、日期分類、重複檔案偵測、即時資料夾監控、操作復原、批次重新命名、全文深度檢索、pHash 相似圖片對比，以及磁碟空間視覺化分析。

---

## 環境需求

| 工具   | 版本  |
|--------|-------|
| JDK    | 21+   |
| Maven  | 3.9+  |

---

## 快速開始

```bash
git clone https://github.com/<你的帳號>/file-organizer.git
cd file-organizer
mvn javafx:run
```

---

## 專案結構

```
src/
├── main/
│   ├── java/com/fileorganizer/
│   │   ├── App.java                              # JavaFX 進入點
│   │   ├── Launcher.java                         # 繞過模組檢查的啟動包裝
│   │   ├── config/
│   │   │   ├── AppConfig.java                    # 設定資料模型（對應 config.json）
│   │   │   └── ConfigLoader.java                 # Gson 序列化讀寫，支援 Path TypeAdapter
│   │   ├── controller/
│   │   │   ├── MainController.java               # JavaFX FXML Controller（UI 互動邏輯）
│   │   │   ├── BatchRenameController.java        # 批次重新命名對話框 Controller
│   │   │   ├── DiskDashboardController.java      # 磁碟空間分析視窗 Controller
│   │   │   └── DuplicateActionDialog.java        # 重複檔案處理選項對話框
│   │   ├── model/
│   │   │   ├── FileItem.java                     # 單一檔案資料模型
│   │   │   ├── FileCategory.java                 # 檔案類別列舉（圖片、影片…）
│   │   │   ├── FileStatus.java                   # 檔案處理狀態列舉（PENDING、MOVED…）
│   │   │   ├── OrganizeResult.java               # 一次整理操作的完整結果
│   │   │   ├── DiskStats.java                    # 磁碟分析結果（含 CategoryStats）
│   │   │   └── FolderStats.java                  # 資料夾樹狀結構節點
│   │   ├── rule/
│   │   │   ├── Rule.java                         # 規則介面
│   │   │   ├── RuleEngine.java                   # 依優先序套用規則，決定 destinationPath
│   │   │   ├── ExtensionRule.java                # 預設規則：依副檔名分類
│   │   │   └── DateRule.java                     # 依修改日期（年/月）分類
│   │   ├── service/
│   │   │   ├── FileScanService.java              # 掃描介面
│   │   │   ├── FileMoveService.java              # 搬移介面（含 Undo）
│   │   │   ├── DuplicateDetectService.java       # 重複偵測介面
│   │   │   ├── LogService.java                   # 日誌讀寫介面
│   │   │   ├── WatchService.java                 # 資料夾監控介面
│   │   │   ├── DiskAnalysisService.java          # 磁碟分析介面
│   │   │   └── FileOrganizerService.java         # UI 層對後端的統一介面（預留擴充）
│   │   ├── service/impl/
│   │   │   ├── FileScanServiceImpl.java          # 掃描實作（BasicFileAttributes）
│   │   │   ├── FileMoveServiceImpl.java          # 搬移實作（含 MoveHistory Undo）
│   │   │   ├── DuplicateDetectServiceImpl.java   # MD5 重複偵測實作
│   │   │   ├── LogServiceImpl.java               # SQLite 日誌實作
│   │   │   ├── FolderWatchServiceImpl.java       # NIO WatchService 監控實作
│   │   │   ├── DiskAnalysisServiceImpl.java      # 非同步磁碟掃描（parallel Files.walk）
│   │   │   ├── OrganizerFacade.java              # 整合橋接層（UI 與後端的唯一入口）
│   │   │   └── AppContext.java                   # 應用程式組裝中心（簡易 DI）
│   │   └── util/
│   │       ├── FileHashUtil.java                 # MD5 計算工具（串流讀取，不 OOM）
│   │       ├── FileSizeUtil.java                 # Bytes 轉人類可讀格式工具
│   │       ├── FileTextExtractor.java            # 全文擷取（.txt / .md / .docx / .pdf）
│   │       └── ImagePHash.java                   # 感知雜湊（pHash）相似圖片比對工具
│   └── resources/
│       ├── fxml/
│       │   ├── main.fxml                         # 主視窗 FXML 佈局
│       │   ├── BatchRename.fxml                  # 批次重新命名對話框 FXML
│       │   └── DiskDashboard.fxml                # 磁碟空間分析視窗 FXML
│       └── style.css                             # JavaFX CSS 樣式
└── test/
    └── java/com/fileorganizer/
        ├── RuleEngineTest.java                   # RuleEngine 單元測試
        ├── LogServiceTest.java                   # LogService 整合測試
        └── config/ConfigLoaderTest.java          # ConfigLoader 單元測試
```

---

## 架構概覽

```
┌─────────────────────────────────────────────────────────┐
│  MainController（JavaFX UI）                             │
│  - 拖曳 / 點選資料夾                                      │
│  - 預覽模式 CheckBox                                      │
│  - 掃描深度 Spinner（1–10 層）                            │
│  - 即時監控 CheckBox                                      │
│  - 開始整理 / 復原 按鈕                                   │
│  - 批次重新命名 按鈕 → BatchRenameController              │
│  - 磁碟分析 按鈕   → DiskDashboardController             │
│  - 全文深度檢索 / pHash 相似圖片對比                      │
└────────────────────────┬────────────────────────────────┘
                         │ AppContext.get().getFacade()
                         ▼
┌────────────────────────────────────────────────────────┐
│  OrganizerFacade（整合橋接層）                           │
│  - scanAsync(directory, depth)                          │
│  - organizeAsync(dryRun, duplicateAction)               │
│  - undoAsync()                                          │
│  - startWatch() / stopWatch()                           │
│  - analyzeDisk() / buildFolderTree()                    │
│  - 暴露 ObservableList / BooleanProperty                │
└───┬─────────────┬──────────────┬───────────────────────┘
    │             │              │
    ▼             ▼              ▼
FileScan      FileMove      DuplicateDetect
Service       Service       Service
    │             │              │
    ▼             ▼              ▼
掃描檔案      搬移 + Undo    MD5 比對
    │
    ▼
RuleEngine
（ExtensionRule / DateRule）
    │
    ▼
destinationPath 寫入 FileItem
```

### 呼叫順序

```
FileScanService.scanRecursive(dir, depth)   // 掃描，回傳 FileItem 清單
DuplicateDetectService.detectDuplicates()   // 計算 MD5，標記 DUPLICATE（可選）
RuleEngine.applyAll()                       // 依規則設定 destinationPath
FileMoveService.move(items, dryRun)         // 實際搬移或預覽
LogService.save(result)                     // 寫入 SQLite 日誌（dryRun 時略過）
```

---

## 分工

| 角色         | 負責人 | 主要檔案 |
|--------------|--------|----------|
| UI 介面      | A      | `controller/MainController.java`、`controller/BatchRenameController.java`、`resources/fxml/`、`resources/style.css` |
| 後端邏輯     | B      | `service/impl/FileScanServiceImpl`、`FileMoveServiceImpl`、`DuplicateDetectServiceImpl` |
| 整合 / 規則  | C      | `rule/`、`config/`、`util/`、`service/impl/OrganizerFacade`、`AppContext`、`FolderWatchServiceImpl`、`LogServiceImpl`、`DiskAnalysisServiceImpl` |

---

## 功能說明

### 分類規則

| 規則            | 優先序      | 說明 |
|-----------------|-------------|------|
| `DateRule`      | 50（較高）  | 依檔案修改日期放入 `YYYY/MM/` 子資料夾 |
| `ExtensionRule` | 100（較低） | 依副檔名放入 `圖片`、`影片`、`音樂`、`文件`、`壓縮檔`、`程式碼`、`其他` |

規則模式可在 `AppConfig.activeRuleMode` 設定：
- `"extension"`（預設）— 只套用 ExtensionRule
- `"date"` — 只套用 DateRule
- `"custom"` — DateRule + ExtensionRule 疊加（先依日期，再依副檔名）

### 掃描深度

Spinner 控制掃描子資料夾的層數（1–10），`1` 表示只掃最上層，不進入子資料夾。

### 預覽模式（Dry Run）

勾選「預覽模式」後，整理流程完整執行但不移動任何檔案，`FileItem` 狀態標為 `MOVED` 供 UI 預覽，且不寫入日誌。

### 重複檔案偵測

對所有 `FileItem` 計算 MD5 checksum，內容相同的檔案標為 `DUPLICATE`。按下「開始整理」時會跳出對話框選擇處理方式：
- **移到分類子資料夾**：例如 `圖片/重複檔案/`
- **統一移到根目錄**：`整理結果/重複檔案/`
- **全部刪除**（謹慎使用）

### 即時監控

`FolderWatchServiceImpl` 以背景 daemon 執行緒透過 Java NIO `WatchService` 監控資料夾，偵測到新檔案時自動觸發重新掃描，回呼切回 JavaFX UI 執行緒執行。

### 操作復原（Undo）

`FileMoveServiceImpl` 記錄每次搬移的 `MoveHistory`，可一鍵反向復原上一次整理的所有搬移操作。

### 批次重新命名

點擊「✏ 批次重新命名」（TableView 有選取則只處理選取列，否則處理全部）開啟獨立對話框，提供三種模式：

| 模式         | 說明 |
|--------------|------|
| 序號附加     | 自訂前綴、後綴、起始序號、步進值、數字補零位數，可選擇保留原檔名 |
| 大小寫轉換   | 全大寫 / 全小寫 / Title Case，副檔名不受影響 |
| 正規式取代   | 輸入 Regex 搜尋模式與取代字串，即時預覽 |

對話框內建衝突偵測（同目錄重複預覽名稱 + 磁碟現有檔案），有衝突時停用「確認執行」。

### 全文深度檢索

輸入關鍵字後點擊「搜尋內容」，對目前資料夾內的 `.txt`、`.md`、`.docx`、`.pdf` 進行內文掃描，並在日誌區顯示含關鍵字的前後文片段（±15 字）。

### pHash 相似圖片對比

點擊「📸 對比相似圖片」，對目前資料夾內所有 `.jpg`、`.jpeg`、`.png` 計算 8×8 感知雜湊（pHash），漢明距離相似度 ≥ 85% 的組合會標示於日誌區並加入 TableView（狀態 `DUPLICATE`）。

### 磁碟空間分析

點擊主介面的「磁碟分析」按鈕開啟獨立視窗，功能包含：
- **類型分佈圓餅圖**：圖片、影片、音樂、文件、程式碼、壓縮檔、其他
- **資料夾 Top 10 列表**：依大小排序，附帶進度條視覺化
- **統計卡片**：總大小、總檔案數、最大類別
- 非同步掃描（`parallel Files.walk`），每 200 個檔案回報一次進度

---

## 設定檔

設定存放於使用者 home 目錄：

```
~/.fileorganizer/
├── config.json    # 應用程式設定（Gson 序列化）
└── log.db         # SQLite 操作日誌
```

`config.json` 範例：

```json
{
  "sourceDirectory": "/Users/alice/Downloads",
  "targetDirectory": "/Users/alice/Organized",
  "watchEnabled": false,
  "dryRunDefault": true,
  "detectDuplicates": true,
  "activeRuleMode": "extension",
  "scanDepth": 1
}
```

---

## 測試

```bash
mvn test
```

| 測試類別          | 涵蓋範圍 |
|-------------------|----------|
| `RuleEngineTest`  | ExtensionRule 分類正確性、DateRule 優先序 |
| `LogServiceTest`  | SQLite save / getRecent / clearAll |
| `ConfigLoaderTest`| Path 序列化往返、首次啟動預設值、JSON 損毀容錯、boolean 欄位往返 |

---

## 依賴

| 函式庫               | 版本       | 用途 |
|----------------------|------------|------|
| JavaFX Controls+FXML | 21.0.2     | UI 框架 |
| Apache POI (ooxml)   | 5.2.5      | .docx 全文擷取 |
| Apache PDFBox        | 3.0.1      | .pdf 全文擷取 |
| Gson                 | 2.10.1     | config.json 序列化 |
| sqlite-jdbc          | 3.45.1.0   | 操作日誌儲存 |
| JUnit Jupiter        | 5.10.2     | 單元測試 |
| Mockito              | 5.11.0     | 測試 Mock |
