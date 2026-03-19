package com.onerag.document.controller;

import com.onerag.document.dto.ParseResult;
import com.onerag.document.service.ChunkingService;
import com.onerag.document.service.TikaParseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    @Autowired
    private TikaParseService tikaParseService;
    
    @Autowired
    private ChunkingService chunkingService;

    /**
     * 解析上传的文档，返回文本和元数据（不存储到 RustFS）
     *
     * POST /api/document/parse
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseDocument(@RequestParam("file") MultipartFile file) {
        ParseResult result = tikaParseService.parseFile(file);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 解析上传的文档并存储到 RustFS
     *
     * POST /api/document/parse-and-upload
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/parse-and-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseAndUploadDocument(@RequestParam("file") MultipartFile file) {
        ParseResult result = tikaParseService.parseAndUploadToFile(file);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 解析上传的文档、存储到 RustFS 并进行分块
     *
     * POST /api/document/parse-upload-chunk
     * Content-Type: multipart/form-data
     * 
     * @param file 上传的文件
     * @param chunkSize 分块大小（可选，默认：500）
     * @param overlapSize 重叠大小（可选，默认：50）
     * @param strategy 分块策略（可选，默认：fixed-size）
     */
    @PostMapping(value = "/parse-upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResult> parseUploadAndChunkDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkSize", defaultValue = "500") int chunkSize,
            @RequestParam(value = "overlapSize", defaultValue = "50") int overlapSize,
            @RequestParam(value = "strategy", defaultValue = "fixed-size") String strategy) {
        
        ParseResult result = tikaParseService.parseUploadAndChunk(file, strategy, chunkSize, overlapSize);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 仅检测文件的 MIME 类型
     *
     * POST /api/document/detect
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> detectMimeType(@RequestParam("file") MultipartFile file) {
        try {
            String mimeType = tikaParseService.detectMimeType(file);

            Map<String, String> response = new HashMap<>();
            response.put("filename", file.getOriginalFilename());
            response.put("mimeType", mimeType);
            response.put("size", String.valueOf(file.getSize()));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "无法检测文件类型: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
