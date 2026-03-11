package com.a05.aiinterview.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表，对应 db-schema 的 users。
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
