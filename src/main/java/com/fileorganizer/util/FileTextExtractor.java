package com.fileorganizer.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;

public class FileTextExtractor {

    public static String extractText(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".txt") || name.endsWith(".md")) {
                return Files.readString(file.toPath());
            } else if (name.endsWith(".docx")) {
                try (FileInputStream fis = new FileInputStream(file);
                     XWPFDocument doc = new XWPFDocument(fis);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    return extractor.getText();
                }
            } else if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            }
        } catch (Exception e) {
            System.err.println("無法讀取檔案內文: " + file.getName() + " -> " + e.getMessage());
        }
        return "";
    }
}