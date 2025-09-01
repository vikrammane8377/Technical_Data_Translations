package com.hackerx.translation.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hackerx.translation.service.TranslationService;

@CrossOrigin(origins = {
    "http://localhost:3000", 
    "https://translater-hackerx-frontend-gzxb6nl4qq-uc.a.run.app",
    "https://translater-hackerx-gzxb6nl4qq-el.a.run.app"
}, allowCredentials = "true")
@RestController
@RequestMapping("/api/translate")
public class TranslationController {

    @Autowired
    private TranslationService translationService;
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationController.class);
    private static final long TIMEOUT = 3600000; // 1 hour in milliseconds

    @PostMapping("/single")
    public DeferredResult<Object> translateSingleFile(@RequestParam("file") MultipartFile file,
            @RequestParam("language") String language, 
            @RequestParam("service") String service,
            @RequestParam(value = "fileType", defaultValue = "json") String fileType) throws IOException {
        
        LOGGER.info("Received single file translation request. File type: {}", fileType);
        
        DeferredResult<Object> result = new DeferredResult<>(TIMEOUT);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                if ("ndjson".equalsIgnoreCase(fileType)) {
                    // Process as NDJSON with batch processing
                    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                    return translationService.translateNDJsonBatch(content, language, service);
                } else {
                    // Process as regular JSON
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> jsonContent = objectMapper.readValue(file.getInputStream(), Map.class);
                    return translationService.translateJson(jsonContent, language, service);
                }
            } catch (IOException e) {
                LOGGER.error("Error processing file: ", e);
                throw new RuntimeException("Error processing file", e);
            }
        }).whenComplete((response, ex) -> {
            if (ex != null) {
                result.setErrorResult(ex);
            } else {
                result.setResult(response);
            }
        });
        
        return result;
    }

    @PostMapping("/app-json")
    public DeferredResult<Map<String, String>> translateAppJSON(@RequestParam("file") MultipartFile file,
            @RequestParam("language") String language, 
            @RequestParam("service") String service) throws IOException {
        
        DeferredResult<Map<String, String>> result = new DeferredResult<>(TIMEOUT);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, String> jsonContent = objectMapper.readValue(file.getInputStream(), 
                        new TypeReference<Map<String, String>>() {});
                
                LOGGER.info("Received app JSON translation request with {} keys", jsonContent.size());
                return translationService.translateAppJson(jsonContent, language, service);
            } catch (IOException e) {
                LOGGER.error("Error processing file: ", e);
                throw new RuntimeException("Error processing file", e);
            }
        }).whenComplete((response, ex) -> {
            if (ex != null) {
                result.setErrorResult(ex);
            } else {
                result.setResult(response);
            }
        });
        
        return result;
    }

    @PostMapping("/multiple")
    public DeferredResult<ResponseEntity<InputStreamResource>> translateMultipleFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("language") String language, 
            @RequestParam("service") String service,
            @RequestParam(value = "fileType", defaultValue = "json") String fileType) throws IOException {
        
        LOGGER.info("Received multiple files translation request. File type: {}", fileType);
        DeferredResult<ResponseEntity<InputStreamResource>> result = new DeferredResult<>(TIMEOUT);
        
        CompletableFuture.supplyAsync(() -> {
            try {
                Path zipFilePath = Paths.get("translated_files.zip");
                
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    
                    for (MultipartFile file : files) {
                        String fileName = file.getOriginalFilename();
                        
                        if ("ndjson".equalsIgnoreCase(fileType)) {
                            // Process as NDJSON with batch processing
                            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                            List<Map<String, Object>> translatedNdjson = translationService.translateNDJsonBatch(content, language, service);
                            
                            // Convert back to NDJSON format
                            StringBuilder ndjsonContent = new StringBuilder();
                            for (Map<String, Object> json : translatedNdjson) {
                                ndjsonContent.append(objectMapper.writeValueAsString(json)).append("\n");
                            }
                            
                            zos.putNextEntry(new ZipEntry(fileName));
                            zos.write(ndjsonContent.toString().getBytes(StandardCharsets.UTF_8));
                            zos.closeEntry();
                        } else {
                            // Process as regular JSON
                            Map<String, Object> jsonContent = objectMapper.readValue(file.getInputStream(), Map.class);
                            Map<String, Object> translatedJson = translationService.translateJson(jsonContent, language, service);
                            
                            zos.putNextEntry(new ZipEntry(fileName));
                            zos.write(objectMapper.writeValueAsBytes(translatedJson));
                            zos.closeEntry();
                        }
                    }
                }
                
                InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFilePath.toFile()));
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=translated_files.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            } catch (Exception e) {
                LOGGER.error("Error processing files: ", e);
                throw new RuntimeException("Error processing files", e);
            }
        }).whenComplete((response, ex) -> {
            if (ex != null) {
                result.setErrorResult(ex);
            } else {
                result.setResult(response);
            }
        });
        
        return result;
    }
}
