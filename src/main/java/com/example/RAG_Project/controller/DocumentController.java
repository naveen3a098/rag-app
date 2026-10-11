package com.example.RAG_Project.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/docs")
public class DocumentController {

    private final VectorStore vectorStore;

    public DocumentController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            filename = "unknown";
        }
        String lower = filename.toLowerCase(Locale.ROOT);

        Resource resource = new InputStreamResource(file.getInputStream());
        List<Document> documents;

        if (lower.endsWith(".pdf")) {
            // Page-aware PDF reader: keeps page numbers in metadata
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            documents = pdfReader.get();
        } else if (lower.endsWith(".docx") || lower.endsWith(".doc")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls")
                || lower.endsWith(".pptx") || lower.endsWith(".ppt")
                || lower.endsWith(".txt") || lower.endsWith(".csv")) {
            // Tika handles Word, Excel, PowerPoint, plain text, CSV, and more
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            documents = tikaReader.get();
        } else {
            return "Unsupported file type: " + filename
                    + ". Supported: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, CSV.";
        }

        if (documents.isEmpty() || documents.stream().allMatch(d -> d.getText() == null || d.getText().isBlank())) {
            return "No readable text found in: " + filename;
        }

        TextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        vectorStore.add(chunks);

        return "Indexed " + chunks.size() + " chunks from " + filename;
    }
}