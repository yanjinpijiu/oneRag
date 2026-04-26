package com.onerag.document.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识库文档元数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("kb_document")
public class KbDocumentDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String documentId;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String s3Bucket;
    private String s3ObjectKey;
    private String fileUrl;
    private String status;
    private Integer chunkCount;
    private Integer vectorCount;
    private String errorMessage;
    private String createdBy;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
