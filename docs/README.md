# 文档总览

当前仓库文档分三层：

1. `现行`
   只记录当前工作区已经存在、可联调、可验证的事实
2. `归档`
   保留历史方案、路线图、冲突记录、审计结论，供追溯参考
3. `工程记录`
   `docs/superpowers/**` 下的规格和执行计划，不作为现行说明

## 现行文档

- [README.md](/D:/a05-cursor/README.md)
  仓库总览与本地联调入口
- [backend/README.md](/D:/a05-cursor/backend/README.md)
  后端结构、配置和启动说明
- [frontend/README.md](/D:/a05-cursor/frontend/README.md)
  前端结构、API 基地址和页面入口
- [product-scope.md](/D:/a05-cursor/docs/product-scope.md)
  当前产品范围与主流程
- [api-design.md](/D:/a05-cursor/docs/api-design.md)
  当前 HTTP / SSE / 语音相关接口
- [page-list.md](/D:/a05-cursor/docs/page-list.md)
  当前前端页面、状态流转和入口关系
- [db-schema.md](/D:/a05-cursor/docs/db-schema.md)
  当前数据库脚本、表结构和迁移说明
- [project-structure.md](/D:/a05-cursor/docs/project-structure.md)
  当前仓库结构与目录职责
- [prompt-strategy.md](/D:/a05-cursor/docs/prompt-strategy.md)
  当前 Prompt 集合与后端编排职责

## 归档文档

历史设计、计划、冲突决策和审计材料已迁入：

- [archive/README.md](/D:/a05-cursor/docs/archive/README.md)

这些文档只作为历史参考，不再代表当前实现。

## 工程记录

以下内容保留原位，但不应当作现行说明使用：

- [docs/superpowers/specs](/D:/a05-cursor/docs/superpowers/specs)
- [docs/superpowers/plans](/D:/a05-cursor/docs/superpowers/plans)

它们用于记录规格、计划和执行痕迹。

## 维护规则

- 现行文档只能写当前工作区中已经存在的实现
- 未来方案、理想架构、待定路线，一律放到归档或工程记录
- 如果一个历史结论仍有价值，应在现行文档中以“历史参考”链接引用，而不是直接把历史文档放进主阅读路径
