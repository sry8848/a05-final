# Qdrant Hybrid Cutover Runbook

## 文档目的

本文用于指导面试 RAG 从旧 collection 切换到当前 hybrid collection 主链路。

当前现行链路是：

- dense 独立召回
- sparse/BM25 独立召回
- RRF 融合
- 商业 rerank
- 程序硬护栏

本文只记录你需要手动执行和人工确认的步骤；不会把人工 cutover 假装成代码代理已经完成。

## 1. 人工前置检查

在进入 cutover 前，先完成下面 4 项人工检查：

1. 确认 Qdrant 服务端支持 sparse/BM25 与 named sparse vector。
2. 确认 Qdrant client 与 server 版本兼容。
当前仓库 `backend/pom.xml` 里锁定的是 `io.qdrant:client:1.13.0`。
如果你的服务端主版本不一致，或次版本差距过大，先升级服务端或客户端，再继续 cutover。
3. 确认是否保留旧 collection 备份，以及备份保留多久。
4. 确认你有可用的 embedding API Key。
当前入库会真实调用 embedding；如果需要在线 rerank，也要确认 rerank 所需配置有效。

## 2. 启动前配置项

当前默认配置来自 [application.yml](D:\a05-cursor\backend\src\main\resources\application.yml)：

- `RAG_ENABLED`
- `QDRANT_HOST`
- `QDRANT_PORT`
- `QDRANT_COLLECTION`
- `QDRANT_DENSE_VECTOR_NAME`
- `QDRANT_DENSE_VECTOR_SIZE`
- `QDRANT_SPARSE_VECTOR_NAME`
- `RAG_DENSE_TOP_K`
- `RAG_SPARSE_TOP_K`
- `RAG_FUSION_TOP_K`
- `RAG_RERANK_*`

当前默认值是：

- collection：`interview_knowledge_hybrid`
- dense vector：`dense`
- dense vector size：`1024`
- sparse vector：`bm25`
- dense/sparse/fusion TopK：`20 / 20 / 20`

首次创建新 collection 时，建议显式指定一个候选 collection 名称，而不是直接覆盖现网默认名。例如：

- `interview_knowledge_hybrid_candidate_20260406`

首次建库阶段建议显式开启：

- `RAG_ENABLED=true`
- `RAG_INITIALIZE_SCHEMA=true`

如果 schema 稳定且后续不想让应用启动时再碰 collection，可以在切换完成后把：

- `RAG_INITIALIZE_SCHEMA=false`

## 3. 新 collection 创建与 schema 验证

### 3.1 创建候选 collection

把 `QDRANT_COLLECTION` 指向新的候选 collection，然后启动应用。

当前应用会通过原生 Qdrant Java Client 自动初始化 schema，预期会创建：

- named dense vector：`dense`
- named sparse vector：`bm25`
- payload 索引：`question_type`、`domain_code`、`active`

### 3.2 验证 schema

如果你的 Qdrant 开放了默认 REST 端口 `6333`，可以用 PowerShell 检查：

```powershell
$collection = "interview_knowledge_hybrid_candidate_20260406"
Invoke-RestMethod -Method Get -Uri "http://localhost:6333/collections/$collection"
```

你需要人工确认：

- collection 已存在
- named dense vector 名称正确
- named sparse vector 名称正确
- payload schema 至少包含 `question_type`、`domain_code`、`active`

如果你不是用默认 REST 端口，或者只开放了 gRPC，请改用你当前的 Qdrant 管理方式完成相同确认。

## 4. 数据重建

当前仓库里可用的数据重建入口有两类：

1. 内部接口：
- `POST /api/v1/admin/knowledge/ingest`
- `POST /api/v1/admin/knowledge/import-jsonl`

2. 开发样本加载：
- `rag.init-sample-data=true`

生产 cutover 不建议依赖样本加载；应使用你当前真实题卡导入流程。

### 4.1 推荐导入方式

如果你当前维护的是 JSONL 题卡，优先使用：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/knowledge/import-jsonl" `
  -Form @{ file = Get-Item "D:\path\to\knowledge.jsonl" }
```

如果你的内部网关或服务已启用认证，请按你当前后台管理方式附带认证头。

### 4.2 导入后人工确认

导入完成后，至少人工确认：

- 导入结果返回成功
- 实际写入条数符合预期
- 应用日志中没有 `Qdrant hybrid upsert 失败`
- 没有出现 “Not existing vector name” 这类 schema 不匹配错误

## 5. 切换前验收

当前仓库已经删除旧的主观评测夹具，因此切换前验收请按“代码回归 + 业务抽样”执行。

### 5.1 代码回归

先运行当前核心回归：

```powershell
rtk mvn -q "-Dtest=RagConfigurationTest,QdrantHybridCollectionManagerTest,KnowledgeDocumentTest,KnowledgeIngestionServiceTest,KnowledgeJsonlImportServiceTest,RagPlanCompilerTest,RagContextTest,RrfFusionTest,RagRetrievalServiceImplTest,QuestionStreamServiceBuildInputTest,AnswerSubmitServiceDecisionFlowTest" test
```

### 5.2 业务抽样

再在候选 collection 上做人工业务抽样，至少覆盖：

- 1 个原理题
- 1 个场景题
- 1 个行为题
- 1 个项目题

你需要人工确认：

- 检索链路会触发时，应用没有报错
- 行为题不会被明显技术题污染
- 项目题不会退化成脱离项目上下文的泛题
- 候选 collection 上的检索结果与你预期一致

如果你已开启调试输出，也建议检查 `retrievalAudit` 是否出现：

- `denseCandidateCount`
- `sparseCandidateCount`
- `fusionTopQuestionIds`
- `rerankPreTopQuestionIds`
- `rerankPostTopQuestionIds`
- `injectedQuestionIds`

## 6. 切换默认 collection

当候选 collection 验收通过后，再把运行环境里的：

- `QDRANT_COLLECTION`

切换到候选 collection 名称，并重启应用实例。

切换后再次做最小业务抽样，确认新实例已经使用新的 collection。

## 7. 删除旧 collection 前的确认项

在删除旧 collection 之前，必须人工确认：

1. 新 collection 已成为唯一默认 collection。
2. 新 collection 已完成至少一轮业务抽样验收。
3. 你已经确认是否保留旧 collection 备份。
4. 没有实例仍然指向旧 collection。

只要上面任何一条没有确认，就不要删除旧 collection。

## 8. 删除旧 collection

确认完成后，再手动删除旧 collection。示例：

```powershell
$old = "interview_knowledge_old"
Invoke-RestMethod -Method Delete -Uri "http://localhost:6333/collections/$old"
```

如果你使用的是其他 Qdrant 管理入口，请按你当前方式手动删除。

## 9. 删除后的验收项

删除旧 collection 后，再人工确认：

- 应用仍能正常启动
- 业务抽样仍然能得到正常检索结果
- Qdrant 中不再保留旧 collection
- 当前默认 collection 就是新 collection

## 10. 当前不由代理执行的事项

下面这些动作必须由你手动完成，本次 runbook 只记录，不代执行：

- Qdrant 服务端升级
- 生产环境配置切换
- 数据重建
- 旧 collection 删除
- 最终上线验收
