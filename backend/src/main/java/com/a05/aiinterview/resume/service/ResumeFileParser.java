package com.a05.aiinterview.resume.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * 简历文件解析：PDF 与 DOCX 提取纯文本。
 * PDF 使用 Apache PDFBox，DOCX 使用 Apache POI。
 */
@Slf4j
@Component
public class ResumeFileParser {

    private static final int MAX_TEXT_LENGTH = 500_000;

    /**
     * 根据文件扩展名解析并返回文本，超过长度截断。
     *
     * @param filePath 本地文件路径
     * @return 提取的文本，解析失败返回 null 或抛异常
     */
    public String parse(Path filePath) throws IOException {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("文件不存在或不可读: " + filePath);
        }
        String name = filePath.getFileName().toString().toLowerCase();
        try (InputStream in = Files.newInputStream(filePath)) {
            if (name.endsWith(".pdf")) {
                return parsePdf(in);
            }
            if (name.endsWith(".docx")) {
                return parseDocx(in);
            }
        }
        throw new IllegalArgumentException("不支持的文件格式，仅支持 .pdf 与 .docx");
    }

    /**
     * PDF 解析
     */
    private String parsePdf(InputStream in) throws IOException {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return truncate(text);
        }
    }

    /**
     * DOCX 解析：按段落拼接
     */
    private String parseDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            String text = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
            return truncate(text);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= MAX_TEXT_LENGTH) return s;
        return s.substring(0, MAX_TEXT_LENGTH);
    }
}
