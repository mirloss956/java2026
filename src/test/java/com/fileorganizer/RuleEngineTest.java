package com.fileorganizer;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.rule.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private FileItem item(String filename) {
        return new FileItem(
            Path.of("/downloads/" + filename),
            1024L,
            LocalDateTime.of(2025, 6, 15, 10, 0)
        );
    }

    @Test
    void extensionRule_jpg_goesToImages() {
        RuleEngine engine = new RuleEngine(
            List.of(new ExtensionRule()),
            Path.of("/organized")
        );
        FileItem f = item("photo.jpg");
        engine.apply(f);
        assertTrue(f.getDestinationPath().toString().contains("圖片"));
    }

    @Test
    void extensionRule_mp3_goesToAudio() {
        RuleEngine engine = new RuleEngine(
            List.of(new ExtensionRule()),
            Path.of("/organized")
        );
        FileItem f = item("song.mp3");
        engine.apply(f);
        assertTrue(f.getDestinationPath().toString().contains("音樂"));
    }

    @Test
    void unknownExtension_goesToOther() {
        RuleEngine engine = new RuleEngine(
            List.of(new ExtensionRule()),
            Path.of("/organized")
        );
        FileItem f = item("mystery.xyz");
        engine.apply(f);
        assertTrue(f.getDestinationPath().toString().contains("其他"));
    }

    @Test
    void dateRule_hasHigherPriority_thanExtensionRule() {
        RuleEngine engine = new RuleEngine(
            List.of(new ExtensionRule(), new DateRule()),
            Path.of("/organized")
        );
        FileItem f = item("photo.jpg");
        engine.apply(f);
        // DateRule priority=50 < ExtensionRule priority=100，所以 DateRule 先跑
        assertTrue(f.getDestinationPath().toString().contains("2025"));
    }
}
