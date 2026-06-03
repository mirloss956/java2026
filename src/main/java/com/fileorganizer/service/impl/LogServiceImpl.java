package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.service.LogService;

import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 負責人：C
 * 用 SQLite 儲存每次整理操作的結果。
 * DB 檔案放在 ~/.fileorganizer/log.db
 */
public class LogServiceImpl implements LogService {

    private static final Path DB_DIR  = Path.of(System.getProperty("user.home"), ".fileorganizer");
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR.resolve("log.db");

    public LogServiceImpl() {
        initSchema();
    }

    // --- schema 初始化（只在第一次呼叫時建表）---

    private void initSchema() {
        try {
            Files.createDirectories(DB_DIR);
        } catch (Exception e) {
            throw new RuntimeException("無法建立設定目錄", e);
        }
        String createSessions = """
            CREATE TABLE IF NOT EXISTS organize_session (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                executed_at TEXT    NOT NULL,
                total       INTEGER NOT NULL,
                moved       INTEGER NOT NULL,
                skipped     INTEGER NOT NULL,
                duplicate   INTEGER NOT NULL,
                failed      INTEGER NOT NULL
            )
            """;
        String createFiles = """
            CREATE TABLE IF NOT EXISTS session_file (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id   INTEGER NOT NULL REFERENCES organize_session(id),
                filename     TEXT    NOT NULL,
                source_path  TEXT    NOT NULL,
                dest_path    TEXT,
                status       TEXT    NOT NULL,
                size_bytes   INTEGER NOT NULL
            )
            """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createSessions);
            stmt.execute(createFiles);
        } catch (SQLException e) {
            throw new RuntimeException("DB schema 初始化失敗", e);
        }
    }

    // --- LogService 實作 ---

    @Override
    public void save(OrganizeResult result) {
        String insertSession = """
            INSERT INTO organize_session (executed_at, total, moved, skipped, duplicate, failed)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        String insertFile = """
            INSERT INTO session_file (session_id, filename, source_path, dest_path, status, size_bytes)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                // 1. 寫入 session
                long sessionId;
                try (PreparedStatement ps = conn.prepareStatement(insertSession, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, result.getExecutedAt().toString());
                    ps.setInt(2, result.getTotalCount());
                    ps.setInt(3, result.getMovedCount());
                    ps.setInt(4, result.getSkippedCount());
                    ps.setInt(5, result.getDuplicateCount());
                    ps.setInt(6, result.getFailedCount());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        sessionId = keys.getLong(1);
                    }
                }
                // 2. 寫入每個檔案
                try (PreparedStatement ps = conn.prepareStatement(insertFile)) {
                    for (FileItem item : result.getItems()) {
                        ps.setLong(1, sessionId);
                        ps.setString(2, item.getFileName());
                        ps.setString(3, item.getSourcePath().toString());
                        ps.setString(4, item.getDestinationPath() != null
                                ? item.getDestinationPath().toString() : null);
                        ps.setString(5, item.getStatus().name());
                        ps.setLong(6, item.getSizeBytes());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("日誌寫入失敗", e);
        }
    }

    @Override
    public List<OrganizeResult> getRecent(int limit) {
        String query = """
            SELECT id, executed_at, total, moved, skipped, duplicate, failed
            FROM organize_session
            ORDER BY id DESC
            LIMIT ?
            """;
        String fileQuery = """
            SELECT filename, source_path, dest_path, status, size_bytes
            FROM session_file
            WHERE session_id = ?
            """;
        List<OrganizeResult> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long sessionId = rs.getLong("id");
                    LocalDateTime execAt = LocalDateTime.parse(rs.getString("executed_at"));

                    // 重建 FileItem 清單
                    List<FileItem> items = new ArrayList<>();
                    try (PreparedStatement fps = conn.prepareStatement(fileQuery)) {
                        fps.setLong(1, sessionId);
                        try (ResultSet frs = fps.executeQuery()) {
                            while (frs.next()) {
                                FileItem item = new FileItem(
                                    Path.of(frs.getString("source_path")),
                                    frs.getLong("size_bytes"),
                                    execAt
                                );
                                String destStr = frs.getString("dest_path");
                                if (destStr != null) item.setDestinationPath(Path.of(destStr));
                                item.setStatus(FileStatus.valueOf(frs.getString("status")));
                                items.add(item);
                            }
                        }
                    }
                    results.add(new OrganizeResult(items, execAt));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("日誌查詢失敗", e);
        }
        return results;
    }

    @Override
    public void clearAll() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM session_file");
            stmt.execute("DELETE FROM organize_session");
        } catch (SQLException e) {
            throw new RuntimeException("日誌清除失敗", e);
        }
    }
}
