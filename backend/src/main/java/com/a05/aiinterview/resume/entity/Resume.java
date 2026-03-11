package com.a05.aiinterview.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历实体，对应表 resumes。
 * 多租户：每条记录归属 user_id；每用户最多一份默认简历（is_default=1）。
 */
@Data
@TableName("resumes")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String sourceType;
    private String filePath;
    private String parseStatus;
    private String parsedText;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
