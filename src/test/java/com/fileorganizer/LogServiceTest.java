package com.fileorganizer;

import com.fileorganizer.model.*;
import com.fileorganizer.service.impl.LogServiceImpl;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LogServiceTest {

    private LogServiceImpl logService;

    @BeforeEach
    void setUp() {
        logService = new LogServiceImpl();
        logService.clearAll();
    }

    @Test
    void save_thenGetRecent_returnsResult() {
        FileItem item = new FileItem(Path.of("/downloads/photo.jpg"), 2048L, LocalDateTime.now());
        item.setDestinationPath(Path.of("/organized/圖片/photo.jpg"));
        item.setStatus(FileStatus.MOVED);

        OrganizeResult result = new OrganizeResult(List.of(item), LocalDateTime.now());
        logService.save(result);

        List<OrganizeResult> recent = logService.getRecent(10);
        assertEquals(1, recent.size());
        assertEquals(1, recent.get(0).getMovedCount());
    }

    @Test
    void clearAll_removesAllRecords() {
        FileItem item = new FileItem(Path.of("/a/b.txt"), 100L, LocalDateTime.now());
        item.setStatus(FileStatus.MOVED);
        logService.save(new OrganizeResult(List.of(item), LocalDateTime.now()));

        logService.clearAll();
        assertTrue(logService.getRecent(10).isEmpty());
    }
}
