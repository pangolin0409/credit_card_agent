# CLAUDE.md

信用卡經辦 AI Agent 展示專案（Demo for IT）。經辦透過 AI Agent 完成兩個場景：

- **場景一：新戶核卡初審** — AI 依規則評估申請人，只能建立「初審核卡草稿單」，最終由人類主管放行。
- **場景二：異常刷卡通報封鎖** — AI 分析近期交易，依規則暫時凍結卡片，或在紅線案件升級給資深風控主管。

程式風格規範見 [STYLE.md](STYLE.md)。原始需求見 [spec.md](spec.md)。

---

## 一、技術棧

| 項目 | 選用 |
|---|---|
| 語言 / 框架 | Java 25 + Spring Boot 4.1.2 + Spring AI 2.0.2（MCP Server / MCP Client） |
| LLM | Google Vertex AI Gemini（`spring-ai-starter-model-google-genai`，設定 `spring.ai.google.genai.vertex-ai=true`） |
| 建置工具 | Maven 多模組（含 Maven Wrapper `./mvnw`） |
| 設定檔 | `application.properties`（key=value，禁止 YAML） |
| 資料庫 | PostgreSQL，資料存取使用 `JdbcClient` 撰寫明確 SQL |
| 前端 | 純 HTML + Tailwind CSS（CDN）+ 原生 JavaScript `fetch` |
| 測試 | JUnit 5 + AssertJ + Testcontainers（PostgreSQL） |

## 二、架構：雙 Server 分離

```
┌────────────── agent-server (:8080) ──────────────┐        ┌──────── mcp-server (:8081) ────────┐
│ static/index.html      經辦操作畫面               │        │ @Tool  唯讀查詢 + 受控寫入          │
│ static/supervisor.html 主管放行畫面 ──────────────┼─REST──▶│ /api/supervisor/**（非 @Tool）      │
│ AgentController  POST /api/agent/chat            │        │ Policy 類別（規則與護欄）           │
│ ChatClient + skills/credit_card_sop.md           │──MCP──▶│ JdbcClient ─▶ PostgreSQL            │
│ Vertex AI Gemini                                 │        │ schema.sql / data.sql               │
└──────────────────────────────────────────────────┘        └─────────────────────────────────────┘
```

- **agent-server**：不連資料庫。只透過 MCP Client 呼叫工具，負責 LLM 對話、SOP 載入與前端頁面。
- **mcp-server**：唯一擁有資料庫的服務。對 AI 只暴露 `@Tool`；主管放行是一般 REST API，**絕不註冊為 `@Tool`**，確保 AI 無法自行核准。
- MCP 傳輸協定使用 Streamable HTTP。
- 主管放行頁面由 agent-server 提供，直接呼叫 mcp-server 的 `/api/supervisor/**`（mcp-server 需允許 `http://localhost:8080` 的 CORS）。

### 目錄結構

```
credit_card_ai_agent/
├── pom.xml                         # parent pom（dependencyManagement: Spring AI BOM）
├── mcp-server/
│   └── src/main/
│       ├── java/com/demo/creditcard/mcp/
│       │   ├── cardapplication/    # 場景一：工具、政策、Repository
│       │   ├── fraudcontrol/       # 場景二：工具、政策、Repository
│       │   ├── supervisor/         # 主管放行 REST API
│       │   └── audit/              # 稽核軌跡寫入
│       └── resources/
│           ├── application.properties
│           ├── schema.sql
│           └── data.sql
└── agent-server/
    └── src/main/
        ├── java/com/demo/creditcard/agent/
        └── resources/
            ├── application.properties
            ├── skills/credit_card_sop.md
            └── static/{index.html, supervisor.html}
```

## 三、開發執行順序（嚴格依序）

1. **資料庫建置**：完成 `schema.sql`（5 張表）與 `data.sql`（第七節的 demo 測試資料）。
2. **TDD 開發 MCP Server**：先寫 Tool 整合測試（紅燈）→ 實作 `@Tool`、Policy 與 Repository（綠燈）→ 重構。
3. **TDD 開發 AI Agent Server**：先寫測試，驗證 Skill 載入與工具調度邊界（紅燈）→ 實作 `credit_card_sop.md` 與 Agent API（綠燈）→ 重構。

每一步都必須先看到測試失敗，才能寫實作程式。

## 四、業務規則

金額單位一律為新台幣元（整數）。邊界條件依下表「比較式」欄為準。

### 場景一：新戶核卡初審（依序判斷，命中即停）

| 順序 | 規則 | 比較式 | 結果 |
|---|---|---|---|
| 1 | 洗錢防制 / 內部黑名單 | `is_aml_blacklist = true` | 退件（不看收入與分數） |
| 2 | 負債比（DBR 22 倍） | `remainingDebtCapacity = monthly_income × 22 − existing_unsecured_debt`；`remainingDebtCapacity < 100,000` | 婉拒（負債比過高） |
| 3a | 頂級無限卡 | `monthly_income ≥ 150,000` 且 `credit_score ≥ 750` | 卡別 `INFINITE`，額度 `min(500,000, remainingDebtCapacity)` |
| 3b | 御璽卡 | `monthly_income ≥ 50,000` 且 `credit_score ≥ 650` | 卡別 `SIGNATURE`，額度 `min(150,000, remainingDebtCapacity)` |
| 3c | 未達門檻 | 以上皆不符 | 婉拒 |

- 符合條件時，AI **只能**呼叫 `createApprovalDraft` 建立草稿單（`approval_drafts.status = 'PENDING_REVIEW'`），**不可寫入 `credit_cards`**。
- 主管於 `supervisor.html` 核准後，才由 mcp-server 的 REST API 寫入 `credit_cards`（`status = 'ACTIVE'`）。

### 場景二：異常刷卡封控（依序判斷，命中即停）

時間窗以**該卡最新一筆交易時間**為基準往回推（不使用系統當下時間），確保 demo 結果可重現。

| 順序 | 規則 | 比較式 | AI 動作 |
|---|---|---|---|
| 1 | 超高金額紅線 | 任一筆交易 `amount > 500,000` | **嚴禁鎖卡**。立即向經辦發出緊急警告，呼叫 `escalateToRiskOfficer` 將整理好的交易明細轉交資深風控主管 |
| 2 | 出國旅遊正常消費 | `has_travel_notice = true` 且國外交易的 `country_code` 皆等於 `travel_country` | 判定正常消費，不鎖卡，只建議經辦解除警報 |
| 3 | 未出國境外大額盜刷 | 無旅遊登錄（或登錄國家與交易國家不符）；最新交易往回 1 小時內，`country_code <> 'TW'` 且 `is_online = true` 的交易金額加總 `> 50,000` | 呼叫 `freezeCard` 設為 `TEMP_FROZEN` |
| 4 | 小額測卡攻擊 | 最新交易往回 10 分鐘內，`amount ≤ 50` 的交易筆數 `≥ 3` | 呼叫 `freezeCard` 設為 `TEMP_FROZEN` |
| 5 | 無異常 | 以上皆不符 | 不動作，回報無異常 |

### 護欄（Defense in Depth）

規則同時存在於兩處，**prompt 只是第一道防線，Tool 層必須強制把關**：

1. `credit_card_sop.md`：讓 AI 依 SOP 判斷並說明理由。
2. mcp-server 寫入型 Tool：執行前用 Policy 類別重新驗證，違反即拒絕執行，回傳拒絕原因，並寫入 `audit_logs`（`action_type = 'GUARDRAIL_BLOCKED'`）。
   - `createApprovalDraft`：拒絕黑名單、DBR 不足、卡別與門檻不符、額度超過 `min(卡別上限, remainingDebtCapacity)` 的草稿。
   - `freezeCard`：卡片非 `ACTIVE` 時拒絕；規則引擎判定為 `ESCALATE_TO_RISK_OFFICER` 時拒絕（必須升級）；判定不是 `OVERSEAS_FRAUD` 或 `CARD_TESTING` 時拒絕（避免誤鎖正常卡片）。
3. agent-server：工具執行錯誤（例如查無客戶或卡號）會轉成 `{"error": "..."}` JSON 回給模型，讓 AI 能告知經辦查無資料，而不是讓請求失敗。

## 五、MCP Tool 清單

| Tool | 類型 | 用途 | 稽核 `action_type` |
|---|---|---|---|
| `getApplicantProfile` | 讀 | 回傳申請人財力、信用、黑名單，以及規則引擎的評估結果（`remainingDebtCapacity`、判定、建議卡別與額度） | — |
| `createApprovalDraft` | 寫 | 建立初審核卡草稿單（卡別、額度、評估摘要） | `APPROVAL_DRAFT` |
| `declineApplication` | 寫 | 記錄退件或婉拒原因 | `APPLICATION_DECLINE` |
| `getCardRiskProfile` | 讀 | 回傳卡片狀態、持卡人旅遊登錄、近期交易（最新交易往回 24 小時），以及規則引擎的評估結果（最大單筆金額、1 小時境外網路消費總額、10 分鐘小額筆數、判定） | — |
| `freezeCard` | 寫 | 將卡片設為 `TEMP_FROZEN` 並記錄 `freeze_reason` | `TEMP_FREEZE` |
| `escalateToRiskOfficer` | 寫 | 紅線案件升級，寫入整理好的交易明細摘要 | `ESCALATION` |

非 Tool 的主管 REST API（mcp-server）：

| Method | Path | 稽核 `action_type`（`operator` 為主管代號） |
|---|---|---|
| GET | `/api/supervisor/approval-drafts?status=PENDING_REVIEW` | — |
| POST | `/api/supervisor/approval-drafts/{draftId}/approve` | `DRAFT_APPROVED` |
| POST | `/api/supervisor/approval-drafts/{draftId}/reject` | `DRAFT_REJECTED` |

## 六、資料庫

### DDL / DML 留存規範

- **所有 DDL 只能寫在 `mcp-server/src/main/resources/schema.sql`；所有種子資料 DML 只能寫在 `data.sql`。** 透過 `spring.sql.init.mode=always` 在啟動時載入。
- 禁止在 psql 或 GUI 工具中手動執行未留存的 SQL。任何 schema 或資料調整都要先改上述檔案，再重新啟動套用。
- `schema.sql` 開頭以 `DROP TABLE IF EXISTS ... CASCADE`（依外鍵反向順序）讓 demo 每次啟動都重置為乾淨狀態。
- 禁止使用 JPA/Hibernate 自動產生 DDL。執行期的 DML（INSERT 稽核、UPDATE 卡片狀態）一律以 `JdbcClient` 搭配明確 SQL 寫在 Repository 中，讓所有 SQL 都可在程式碼中被審閱。
- 測試專用資料放在 `src/test/resources/`，同樣以 `.sql` 檔留存。

### 資料表

`customers`、`credit_cards`、`transactions`、`audit_logs` 欄位定義以 [spec.md](spec.md) 第三節為準，另新增：

**`approval_drafts`（初審核卡草稿單）**

| 欄位名稱 | 資料型別 | 約束條件 | 欄位說明 |
|---|---|---|---|
| draft_id | SERIAL | PRIMARY KEY | 草稿單流水號 |
| customer_id | VARCHAR(20) | NOT NULL, FK | 申請人客戶編號（關聯 customers.customer_id） |
| card_type | VARCHAR(30) | NOT NULL | 建議卡別：`INFINITE`、`SIGNATURE` |
| credit_limit | INT | NOT NULL | 建議額度 |
| assessment_summary | TEXT | NOT NULL | AI 評估摘要（命中規則與計算結果） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING_REVIEW' | `PENDING_REVIEW`、`APPROVED`、`REJECTED` |
| created_by | VARCHAR(50) | NOT NULL DEFAULT 'AI_AGENT_MCP' | 建立者 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 建立時間 |
| reviewed_by | VARCHAR(50) | NULL | 放行主管代號 |
| reviewed_at | TIMESTAMP | NULL | 放行時間 |

### 代碼值

| 欄位 | 允許值 |
|---|---|
| `credit_cards.card_type` / `approval_drafts.card_type` | `INFINITE`（頂級無限卡）、`SIGNATURE`（御璽卡） |
| `credit_cards.status` | `ACTIVE`、`TEMP_FROZEN`、`CLOSED` |
| `audit_logs.action_type` | `APPROVAL_DRAFT`、`APPLICATION_DECLINE`、`TEMP_FREEZE`、`ESCALATION`、`GUARDRAIL_BLOCKED`、`DRAFT_APPROVED`、`DRAFT_REJECTED` |

以上代碼值都要用 `CHECK` 約束鎖定。

## 七、Demo 測試情境（`data.sql` 與整合測試共用）

每條規則至少有一筆資料命中，邊界值也要覆蓋。

**場景一：新戶核卡**

| customer_id | 情境 | 關鍵資料 | 預期結果 |
|---|---|---|---|
| `A100000001` | 黑名單高收入 | 黑名單、月收 300,000、分數 800 | 退件 |
| `A100000002` | 負債比過高 | 月收 60,000、負債 1,250,000（剩 70,000） | 婉拒 |
| `A100000003` | 頂級無限卡 | 月收 200,000、分數 780、無負債 | `INFINITE` / 500,000 |
| `A100000004` | 御璽卡 | 月收 80,000、分數 700、無負債 | `SIGNATURE` / 150,000 |
| `A100000005` | 額度受 DBR 限縮 | 月收 60,000、分數 680、負債 1,200,000（剩 120,000） | `SIGNATURE` / 120,000 |
| `A100000006` | 高收入但分數不足無限卡 | 月收 200,000、分數 700 | `SIGNATURE` / 150,000 |
| `A100000007` | 未達門檻 | 月收 40,000、分數 700 | 婉拒 |

**場景二：異常刷卡**

| card_number | 情境 | 關鍵資料 | 預期結果 |
|---|---|---|---|
| `4000000000000001` | 出國旅遊正常 | 登錄 JP，JP 實體大額消費 | 不鎖卡，建議解除警報 |
| `4000000000000002` | 境外盜刷 | 無登錄，1 小時內 US 網路消費 3 筆共 62,000 | `TEMP_FROZEN` |
| `4000000000000003` | 旅遊國家不符 | 登錄 JP，1 小時內 FR 網路消費共 55,000 | `TEMP_FROZEN` |
| `4000000000000004` | 小額測卡 | 10 分鐘內 4 筆 ≤ 50 元 | `TEMP_FROZEN` |
| `4000000000000005` | 超高金額紅線 | 單筆 680,000 | 不鎖卡，升級風控主管 |
| `4000000000000006` | 正常消費 | 國內一般消費 | 無異常 |

## 八、Agent 行為規範（寫入 `credit_card_sop.md`）

- 以繁體中文回覆經辦，結論先講，再列出命中的規則與計算數字。
- 規則、門檻、流程類的一般問答可以直接依 SOP 回答，不需要編號。
- 對特定客戶或卡片做判斷前，必須先呼叫讀取型 Tool 取得資料，禁止臆測數值。
- 對話記憶：前端每個頁面產生一個 `conversationId`，agent-server 以 `MessageChatMemoryAdvisor`（記憶體儲存）保存同一段對話，追問時可沿用先前提過的編號。按「開始新對話」或重新整理頁面就會換成新的對話。
- 一般問答與追問不得觸發寫入型 Tool，只有經辦明確要求審核或處置時才可執行。
- 寫入型 Tool 回傳拒絕時，要如實告知經辦，不可重試或換參數繞過。
- 紅線案件的回覆開頭必須是明顯的緊急警告。

## 九、常用指令

```bash
docker run -d --name credit-card-pg -p 5432:5432 \
  -e POSTGRES_DB=credit_card_demo -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo postgres:17

gcloud auth application-default login

./mvnw test
./mvnw -pl mcp-server test
./mvnw -pl agent-server test
DB_URL=jdbc:postgresql://localhost:5432/credit_card_demo DB_USERNAME=demo DB_PASSWORD=demo \
  ./mvnw -pl mcp-server spring-boot:run

GOOGLE_CLOUD_PROJECT=<gcp-project-id> GOOGLE_CLOUD_LOCATION=global VERTEX_AI_MODEL=gemini-3.6-flash \
  ./mvnw -pl agent-server spring-boot:run
```

開啟 `http://localhost:8080/` 為經辦畫面，`http://localhost:8080/supervisor.html` 為主管放行畫面。

必要環境變數：

| 變數 | 用途 |
|---|---|
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | mcp-server 資料庫連線 |
| `GOOGLE_CLOUD_PROJECT`、`GOOGLE_CLOUD_LOCATION` | Vertex AI 專案與區域 |
| `VERTEX_AI_MODEL` | Gemini 模型名稱 |
| `MCP_SERVER_URL` | agent-server 連線的 MCP Server 位址（預設 `http://localhost:8081`） |

啟動順序：PostgreSQL → mcp-server → agent-server（agent-server 啟動時會連線 MCP Server）。

整合測試使用 Testcontainers，執行前需要啟動 Docker。
