package com.fileorganizer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 負責人：C
 * 計算檔案 MD5 checksum，供 DuplicateDetectService 使用。
 */
public class FileHashUtil {

    private FileHashUtil() {}

    /**
     * 計算 MD5，回傳 hex string（例如 "d41d8cd98f00b204e9800998ecf8427e"）。
     * 大檔案以串流方式讀取，不會 OOM。
     */
    public static String md5(Path path) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("MD5 計算失敗：" + path, e);
        }
    }
}
