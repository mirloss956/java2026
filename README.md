# File Organizer

Java 桌面應用程式（JavaFX），自動整理資料夾中的檔案。

## 環境需求

- JDK 21+
- Maven 3.9+

## 快速開始

```bash
git clone https://github.com/<你的帳號>/file-organizer.git
cd file-organizer
mvn javafx:run
```

## 分工

| 角色 | 負責人 | 目錄 |
|------|--------|------|
| UI 介面 | A | `controller/`、`resources/fxml/`、`resources/css/` |
| 後端邏輯 | B | `service/impl/FileScanServiceImpl`、`FileMoveServiceImpl`、`DuplicateDetectServiceImpl` |
| 整合 / 規則 | C | `rule/`、`config/`、`util/`、`service/impl/FolderWatchServiceImpl`、`service/impl/LogServiceImpl` |

## Git 工作流程

- `main` — 穩定版本，只接受 PR，不直接 push
- `dev/A`、`dev/B`、`dev/C` — 各自開發分支
- 整合時從自己的分支開 PR → merge 進 `main`

## 整合呼叫順序

```
FileScanService.scan()          ← B 實作
DuplicateDetectService.detect() ← B 實作（optional）
RuleEngine.applyAll()           ← C 已實作
FileMoveService.move()          ← B 實作
LogService.save()               ← C 實作
```

## 測試

```bash
mvn test
```
