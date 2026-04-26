package com.onerag.document.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.onerag.document.dto.KbDocumentProcessReq;
import com.onerag.document.dto.KbDocumentQueryReq;
import com.onerag.document.dto.KbDocumentResp;
import com.onerag.document.dto.KbDownloadUrlResp;
import com.onerag.document.service.KnowledgeBaseService;
import com.onerag.user.dto.CommonResponse;
import com.onerag.user.dto.PageResp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/kb/documents")
@RequiredArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public CommonResponse<PageResp<KbDocumentResp>> page(KbDocumentQueryReq req) {
        StpUtil.checkLogin();
        return CommonResponse.ok(knowledgeBaseService.page(req));
    }

    @GetMapping("/{documentId}")
    public CommonResponse<KbDocumentResp> detail(@PathVariable String documentId) {
        StpUtil.checkLogin();
        return CommonResponse.ok(knowledgeBaseService.detail(documentId));
    }

    @PostMapping("/upload")
    public CommonResponse<KbDocumentResp> upload(@RequestParam("file") MultipartFile file) {
        StpUtil.checkLogin();
        String operator = String.valueOf(StpUtil.getLoginId());
        return CommonResponse.ok(knowledgeBaseService.upload(file, operator));
    }

    @PostMapping("/{documentId}/process")
    public CommonResponse<KbDocumentResp> process(@PathVariable String documentId, @RequestBody(required = false) KbDocumentProcessReq req) {
        StpUtil.checkLogin();
        String operator = String.valueOf(StpUtil.getLoginId());
        return CommonResponse.ok(knowledgeBaseService.process(documentId, req, operator));
    }

    @DeleteMapping("/{documentId}")
    public CommonResponse<Void> delete(@PathVariable String documentId) {
        StpUtil.checkLogin();
        String operator = String.valueOf(StpUtil.getLoginId());
        knowledgeBaseService.delete(documentId, operator);
        return CommonResponse.ok(null);
    }

    @GetMapping("/{documentId}/download-url")
    public CommonResponse<KbDownloadUrlResp> downloadUrl(@PathVariable String documentId) {
        StpUtil.checkLogin();
        return CommonResponse.ok(knowledgeBaseService.getDownloadUrl(documentId));
    }
}
