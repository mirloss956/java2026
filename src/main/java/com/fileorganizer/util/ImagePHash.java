package com.fileorganizer.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImagePHash {

    // 將圖片縮小並灰階化，計算出 8x8 的感知雜湊值
    public static String getPHash(File file) {
        try {
            BufferedImage src = ImageIO.read(file);
            if (src == null) return "";

            // 1. 縮放成 8x8 像素（極度簡化圖片細節）
            BufferedImage resized = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.drawImage(src, 0, 0, 8, 8, null);
            g.dispose();

            // 2. 計算 64 個像素的平均灰階亮度
            int total = 0;
            int[] pixels = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int rgb = resized.getRGB(x, y);
                    int gray = (rgb >> 16) & 0xFF; // 取得灰階值
                    pixels[y * 8 + x] = gray;
                    total += gray;
                }
            }
            int avg = total / 64;

            // 3. 與平均值比對：大於平均為 1，小於為 0，組合成 64 位元的二進位字串
            StringBuilder hash = new StringBuilder();
            for (int i = 0; i < 64; i++) {
                hash.append(pixels[i] >= avg ? "1" : "0");
            }
            return hash.toString();
        } catch (IOException e) {
            return "";
        }
    }

    // 計算兩個 pHash 的漢明距離（Hamming Distance），也就是有幾個位元不同
    public static double calculateSimilarity(String hash1, String hash2) {
        if (hash1.isEmpty() || hash2.isEmpty() || hash1.length() != hash2.length()) return 0.0;
        int distance = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                distance++;
            }
        }
        // 漢明距離越小，相似度越高（64位元中完全一樣就是 100%）
        return (64.0 - distance) / 64.0;
    }
}