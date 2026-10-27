package com.docqa.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor {
    public record PageText(Integer page, String text) {}

    public static boolean isSupported(String filename) {
        String f = filename.toLowerCase();
        return f.endsWith(".pdf") || f.endsWith(".txt") || f.endsWith(".md");
    }

    public List<PageText> extract(String filename, byte[] bytes) throws IOException {
        if (filename.toLowerCase().endsWith(".pdf")) {
            List<PageText> pages = new ArrayList<>();
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    pages.add(new PageText(p, stripper.getText(pdf)));
                }
            }
            return pages;
        }
        return List.of(new PageText(null, new String(bytes, StandardCharsets.UTF_8)));
    }
}
