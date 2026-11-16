# 信用卡經辦 AI Agent

這是一個可以實際執行的 Demo 專案，用信用卡經辦的兩個日常作業，示範 AI Agent 如何透過 **MCP**、**Tool**、**Skill** 讀取資料並執行受控的動作：

- **新戶核卡初審**：AI 依規則評估申請人，只能建立「初審核卡草稿單」，最後由主管在放行畫面核准，才會真正發卡。
- **異常刷卡通報封鎖**：AI 分析卡片近期交易，依規則暫時凍結卡片；碰到單筆超過 500,000 元的紅線案件時，禁止鎖卡，改為升級給資深風控主管。

設計原則只有一條：**AI 可以做初審與暫時處置，但核准發卡與紅線案件一定由人決定。**

| 文件 | 內容 |
|---|---|
| [spec.md](spec.md) | 原始需求與資料表定義 |
| [CLAUDE.md](CLAUDE.md) | 完整業務規則、Tool 清單、資料表、Demo 測試資料 |
| [STYLE.md](STYLE.md) | 程式風格規範 |

## 技術棧

Java 25、Spring Boot 4.1.2、Spring AI 2.0.2（MCP Server / MCP Client）、Google Vertex AI Gemini、PostgreSQL（以 `JdbcClient` 撰寫明確 SQL）、純 HTML + Tailwind CSS 前端、JUnit 5 + AssertJ + Testcontainers。

## 架構

```
瀏覽器                     agent-server (:8080)                    mcp-server (:8081)
┌──────────────────┐      ┌──────────────────────────────┐        ┌───────────────────────────┐
│ 經辦畫面          │─────▶│ AgentController              │        │ MCP Server endpoint       │
│ index.html       │      │ SkillRouter + skills/*.md    │        │ @Tool × 6（3 讀 3 寫）     │
│                  │      │ MCP Client + ToolAllowList   │──MCP──▶│ Policy（規則引擎與護欄）    │
│                  │      │ ChatClient + ChatMemory      │        │ Repository（JdbcClient）  │──▶ PostgreSQL
│ 主管放行畫面       │      └──────────────┬───────────────┘        │                           │
│ supervisor.html  │─────── REST（不經 AI、不經 MCP）────────────▶│ /api/supervisor/**        │
└──────────────────┘                     ▼                         └───────────────────────────┘
                                 Vertex AI Gemini
```

- **agent-server** 不連資料庫，只負責 LLM 對話、載入 SOP 與提供前端頁面，所有資料都要透過 MCP 工具取得。
- **mcp-server** 是唯一擁有資料庫的服務。對 AI 只開放 `@Tool`；主管放行是一般 REST API，**沒有註冊成 Tool**，所以 AI 沒有核准的能力。

## MCP、Tool、Skill 在專案中的位置

| 概念 | 回答的問題 | 在專案中的位置 |
|---|---|---|
| **MCP** | AI 連得到哪些系統？ | agent-server 以 MCP Client 透過 Streamable HTTP 連到 mcp-server。設定見兩個模組的 `application.properties` 與 [McpToolConfiguration.java](mcp-server/src/main/java/com/demo/creditcard/mcp/McpToolConfiguration.java) |
| **Tool** | AI 能做哪些動作？ | [CardApplicationTools.java](mcp-server/src/main/java/com/demo/creditcard/mcp/cardapplication/CardApplicationTools.java)、[FraudControlTools.java](mcp-server/src/main/java/com/demo/creditcard/mcp/fraudcontrol/FraudControlTools.java)。寫入型 Tool 執行前會用 Policy 類別重新驗證，違規時拒絕並寫入稽核 `GUARDRAIL_BLOCKED` |
| **Skill** | 什麼情境、照什麼 SOP 使用工具？ | [skills/](agent-server/src/main/resources/skills/) 下的 Markdown SOP。frontmatter 宣告斜線指令名稱、參數與可用工具，由 [SkillRouter.java](agent-server/src/main/java/com/demo/creditcard/agent/skill/SkillRouter.java) 組合 prompt 並限縮工具 |

三者各是一道防線：

1. Skill 用 SOP 引導 AI，並且只開放這個情境需要的工具。
2. Tool 層由程式強制驗證，模型判斷錯了也無法執行違規動作。
3. MCP 的邊界決定了 AI 根本看不到哪些能力，例如主管核准。

## 環境需求

- JDK 25（建置使用專案內附的 Maven Wrapper `./mvnw`，不需要另外安裝 Maven）
- Docker：執行 PostgreSQL，以及跑整合測試（Testcontainers）
- 可以使用 Vertex AI 的 Google Cloud 專案，以及 `gcloud` CLI

## 快速啟動

請依照 **PostgreSQL → mcp-server → agent-server** 的順序啟動。agent-server 啟動時會去連 MCP Server。

**1. 啟動 PostgreSQL**

```bash
docker run -d --name credit-card-pg -p 5432:5432 \
  -e POSTGRES_DB=credit_card_demo -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo postgres:17
```

**2. 啟動 mcp-server**

```bash
DB_URL=jdbc:postgresql://localhost:5432/credit_card_demo DB_USERNAME=demo DB_PASSWORD=demo \
  ./mvnw -pl mcp-server spring-boot:run
```

每次啟動都會用 `schema.sql` 與 `data.sql` 把資料庫重置成乾淨的 demo 狀態，所以 demo 可以重複執行。

**3. 登入 Google Cloud 並啟動 agent-server**

```bash
gcloud auth application-default login

GOOGLE_CLOUD_PROJECT=<gcp-project-id> GOOGLE_CLOUD_LOCATION=global VERTEX_AI_MODEL=gemini-3.6-flash \
  ./mvnw -pl agent-server spring-boot:run
```

**4. 開啟畫面**

- 經辦畫面：<http://localhost:8080/>
- 主管放行畫面：<http://localhost:8080/supervisor.html>

### 環境變數

| 變數 | 用途 |
|---|---|
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | mcp-server 的資料庫連線 |
| `GOOGLE_CLOUD_PROJECT`、`GOOGLE_CLOUD_LOCATION` | Vertex AI 專案與區域 |
| `VERTEX_AI_MODEL` | Gemini 模型名稱 |
| `MCP_SERVER_URL` | agent-server 連線的 MCP Server 位址（預設 `http://localhost:8081`） |

## 經辦畫面怎麼用

在輸入框打 `/` 會出現斜線指令選單：

| 指令 | 用途 | 會不會呼叫 LLM |
|---|---|---|
| `/待審案件` | 列出新戶申請與處理狀態，點選即可帶入初審 | 不會（前端清單） |
| `/警示卡片` | 列出需要處理的通報卡片 | 不會（前端清單） |
| `/核卡初審 客戶編號` | 依規則評估申請人，建立草稿單或記錄婉拒 | 會，只開放 3 個核卡工具 |
| `/刷卡風險分析 卡號` | 分析交易，凍結、升級風控或回報無異常 | 會，只開放 3 個封控工具 |
| `/規則問答 問題` | 詢問門檻、計算方式與流程 | 會，但不開放任何工具 |

- 客戶編號與卡號輸入時會自動完成。如果參數格式不對（客戶編號須為 `^[A-Z]\d{9}$`，卡號須為 `^\d{16}$`），系統會直接回覆提示，不會呼叫 LLM。
- 不打斜線也可以自由提問或追問先前的案件，這時會開放全部 6 個工具，但 SOP 規定一般問答與追問不得觸發寫入。
- 按「開始新對話」或重新整理頁面，就會換成一段新的對話記憶。

## Demo 範例

以下結果都對應 `data.sql` 的種子資料。LLM 的措辭每次可能不同，但工具呼叫的結果與資料庫狀態是固定的。

| # | 在經辦畫面輸入 | 預期結果 |
|---|---|---|
| 1 | `/核卡初審 A100000003` | 建立 `INFINITE` / 500,000 草稿單。到主管畫面核准後，才寫入 `credit_cards` |
| 2 | `/核卡初審 A100000005` | `SIGNATURE` / 120,000：60,000 × 22 − 1,200,000 = 120,000，小於御璽卡上限 150,000 |
| 3 | `/核卡初審 A100000001` | 命中黑名單，退件（不看收入與分數） |
| 4 | `/刷卡風險分析 4000000000000002` | 暫時凍結：1 小時內 US 網路消費 18,000 + 21,000 + 23,000 = 62,000 |
| 5 | `那它為什麼被凍結？` | 沿用上一題的卡號說明原因，不會再觸發寫入 |
| 6 | `/刷卡風險分析 4000000000000005` | 回覆以「【緊急警告】」開頭：單筆 680,000，不鎖卡，升級資深風控主管 |
| 7 | `/刷卡風險分析 4000000000000001` | 有登錄日本旅遊、日本實體消費，判定正常，建議解除警報 |
| 8 | `/規則問答 為什麼單筆超過 50 萬不能由 AI 鎖卡？` | 直接依 SOP 回答，不查詢任何資料 |

完整的測試客戶與卡片清單見 [CLAUDE.md](CLAUDE.md) 第七節。

## 測試

整合測試使用 Testcontainers，執行前請先啟動 Docker。測試不會呼叫真正的 Gemini。

```bash
./mvnw test                   # 全部模組
./mvnw -pl mcp-server test    # 只跑 mcp-server
./mvnw -pl agent-server test  # 只跑 agent-server
```

## 目錄導覽

```
credit_card_ai_agent/
├── pom.xml                         # parent pom（Spring AI BOM）
├── mcp-server/                     # MCP Server，唯一擁有資料庫的服務
│   └── src/main/
│       ├── java/.../mcp/
│       │   ├── cardapplication/    # 場景一：核卡工具、政策、Repository
│       │   ├── fraudcontrol/       # 場景二：封控工具、規則引擎、Repository
│       │   ├── supervisor/         # 主管放行 REST API（非 Tool）
│       │   ├── clerk/              # 經辦清單與搜尋 REST API（非 Tool）
│       │   └── audit/              # 稽核軌跡
│       └── resources/
│           ├── schema.sql          # 所有 DDL
│           └── data.sql            # Demo 種子資料
└── agent-server/                   # AI Agent 與前端頁面
    └── src/main/
        ├── java/.../agent/         # Agent API、工具白名單、Skill 路由
        └── resources/
            ├── skills/             # common.md 與各 Skill 的 SOP
            └── static/             # index.html、supervisor.html
```

## 開發規範摘要

- 所有 DDL 只寫在 `schema.sql`，種子資料只寫在 `data.sql`，禁止手動執行未留存的 SQL。
- 執行期 SQL 一律用 `JdbcClient` 寫在 Repository 中，不使用 JPA 自動產生 DDL。
- 設定檔使用 `application.properties`，不使用 YAML。
- 採用 TDD：先寫測試並看到它失敗，再寫實作。
- 業務規則同時存在於 `skills/*.md` 與 mcp-server 的 Policy 類別。修改規則時兩邊都要改，並補上測試。
