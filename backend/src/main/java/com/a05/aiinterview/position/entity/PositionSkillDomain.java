package com.a05.aiinterview.position.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位知识域实体，对应 position_skill_domains 表。
 * 每行代表某岗位下的一个知识域定义，是静态配置数据。
 */
@Data
@TableName("position_skill_domains")
public class PositionSkillDomain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位编码，如 JAVA_BACKEND */
    private String positionCode;

    /** 岗位中文名，如 Java 后端开发 */
    private String positionName;

    /** 知识域定义版本，升级知识域树时递增 */
    private Integer version;

    /** 知识域编码，如 java_core */
    private String domainCode;

    /** 知识域中文名，如 Java 核心基础 */
    private String domainName;

    /** 知识域说明 */
    private String description;

    /** 展示排序序号，正序 */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
