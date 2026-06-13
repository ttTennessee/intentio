# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Intentio 是一个**声明式、schema 驱动的持久化引擎**（Maven 多模块，Java 17）。
应用不写 SQL，而是用 YAML 定义实体/关系/完整性规则，再提交「意图」（`IntentGroup`，
即一组 insert/update/delete 操作）。引擎负责校验、按引用拓扑排序、在**单个事务**内
逐条执行、跑业务约束，任一步失败即整体回滚。

- `engine-core` — 引擎本身（无 MySQL/Hikari 依赖，测试用 H2）
- `engine-example` — 处方/药品库存示例 + 集成测试（依赖 engine-core、HikariCP、mysql-connector-j）

## 构建与测试

```bash
mvn -q compile                       # 全量编译
mvn test                             # 全部测试
mvn -pl engine-core test             # 只测核心模块（纯 H2，无外部依赖）
mvn -pl engine-example test          # 只测示例模块（见下方数据库注意事项）

# 单个测试 / 单个方法
mvn -pl engine-example test -Dtest=PrescriptionIntegrationTest
mvn -pl engine-example test -Dtest=PrescriptionIntegrationTest#rejectsWhenStockInsufficient
```

**数据库行为（重要）**：集成测试通过 `engine-example/src/test/resources/application.properties`
的 `jdbc.url` 决定后端 —— **该值为空 / 注释掉时自动回退到 H2 内存库**
（`MODE=MySQL`），有值时连真实 MySQL。当前该文件已填入本地 MySQL 连接，所以
`engine-example` 测试默认会尝试连 MySQL；要纯离线跑测试，先注释掉 `jdbc.url`。
`engine-core` 的测试始终走 H2，无此问题。

## 执行流水线（`SchemaEngine.execute`）

理解这条主线就理解了整个引擎，它串起几乎所有类：

1. **校验** `Validator.validate` — 字段类型/必填/枚举/长度、`@ref` 是否指向更早的 op。失败即返回，不开事务。
2. **拓扑排序** `TopologySorter.sort` — 按 `@ref` 依赖排序 op；检测到环则报错。
3. 开启事务（`autoCommit=false`），对排序后的每个 op 顺序处理：
   - **解析引用** `ReferenceResolver` — 把 `@opName` / `@opName.field` 替换成前序 op 在 `VirtualMachine` 中 `PendingRow` 的真实值（通常是自增生成的 id）。
   - 注册 `PendingRow` 到 VM。
   - **前置约束** `ConstraintEngine.checkPre` — `stock_check`、`ref_check`，以及 INSERT 时对所有 `belongs_to` 外键的存在性自动校验。
   - **执行 SQL** `Executor` — 生成 INSERT/UPDATE/DELETE，回填自增主键到 `PendingRow.setGeneratedId`。
4. 所有 op 执行完后跑 **后置约束** `ConstraintEngine.checkPost` —— 目前只有 `require_has`（需要父行已生成 id，再查子表实际行数）。
5. 全部通过 `commit`，否则 `rollback`，返回 `IntentResult`（`ok()` + `generatedIds` 或 `errors`）。

查询是独立路径：`QueryIntent` → `QueryExecutor`，用 `RelationGraph` 把 `include("items.drug")`
这类点分路径规划成 `LEFT JOIN`，再把扁平结果集**折叠成嵌套 Map**（has_many → List，belongs_to/has_one → 单个 Map）。

## 关键概念

- **引用语法**：op 的字段值若为 `@name` 或 `@name.field` 字符串即视为对另一 op 的引用（`ReferenceParser` 正则识别，默认 field 为 `id`）。这是跨 op 串联数据的唯一机制，被校验、拓扑排序、运行时解析三处共同依赖。
- **Schema 加载**：`SchemaLoader` 读取目录或 classpath 下所有 `*.yml`/`*.yaml`，每个文件顶层是 `entity:` 块。实体名重复会报错。`table` 缺省由实体名 camelCase→snake_case 推导。产出 `SchemaRegistry`，并据此构建 `RelationGraph`（jgrapht 有向图）。
- **三类业务规则**（在 schema 的 `integrity:` 下声明，由 `constraint/` 包实现）：
  - `require_has`（后置）—— 父实体必须至少有 N 个某关系的子行。
  - `stock_check`（前置）—— 形如 `"stock.quantity >= item.weight"` 的库存比较，通过 `via`/`fk` 指定的库存表读当前值。表达式由 `StockCheckRule` 内的正则解析，**仅支持该固定形态**。
  - `ref_check`（前置）—— 外键指向的记录必须在库中存在。
- **`FieldType.parse` 接受别名**（如 `int/integer/bigint`→LONG，`varchar/text`→STRING），新增字段类型需同时改 `FieldType`、`Validator.validateValue` 与 `Executor.bindParam` 三处的 switch。

## 约定

- 所有领域类是**手写不可变值对象**（构造器 + getter，无 Lombok）。新增 schema 元素时遵循同样风格。
- 错误不抛异常给调用方，而是收集成 `IntentError`（含 `op` / `entity` / `rule` / `message`）放进 `IntentResult.failure`。`rule` 字段是测试断言的稳定锚点（如 `"require_has"`、`"stock_check"`）。
- jOOQ 已在依赖中但当前 SQL 全为手拼 `PreparedStatement`（`Executor`、`QueryExecutor`、`*Rule`）。

## Agent skills

### Issue tracker

Issues、PRD 存放在仓库的 GitHub Issues（`ttTennessee/intentio`），通过 `gh` CLI 操作。详见 `docs/agents/issue-tracker.md`。

### Triage labels

五个标准 triage 角色直接使用同名标签（`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`）。详见 `docs/agents/triage-labels.md`。

### Domain docs

单一 context 布局：根目录 `CONTEXT.md` + `docs/adr/`。详见 `docs/agents/domain.md`。
