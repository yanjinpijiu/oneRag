package com.onerag.document.service;

import com.onerag.document.dto.KbDocumentProcessReq;
import com.onerag.document.dto.KbDocumentQueryReq;
import com.onerag.document.dto.KbDocumentResp;
import com.onerag.document.dto.KbDownloadUrlResp;
import com.onerag.user.dto.PageResp;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeBaseService {
    PageResp<KbDocumentResp> page(KbDocumentQueryReq req);

    KbDocumentResp upload(MultipartFile file, String operator);

    KbDocumentResp detail(String documentId);

    KbDocumentResp process(String documentId, KbDocumentProcessReq req, String operator);

    void delete(String documentId, String operator);

    KbDownloadUrlResp getDownloadUrl(String documentId);
}
