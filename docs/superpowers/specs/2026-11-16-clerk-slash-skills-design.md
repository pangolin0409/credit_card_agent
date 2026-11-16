# 經辦斜線指令 Skill 設計

日期：2026-11-16
分支：`feature/clerk-slash-skills`

## 1. 目的與動機

目前經辦要在下拉選單挑客戶編號或卡號，或在自由提問框自己打編號。實際上經辦不會記得 10 碼客戶編號或 16 碼卡號，demo 資料中也有大量同姓客戶（林、陳、張、黃、吳、李），只靠姓名很容易找錯人。

這次要讓經辦用**有明確名稱的 skill** 來操作 AI，並且**幾乎不用打字**：

- skill 名稱同時出現在畫面（斜線選單）與 prompt（SOP 檔案標題），兩邊用同一套名稱。
- 經辦主要從「待辦清單」點選案件，系統自動送出對應的 skill 指令；需要找特定對象時，用斜線指令後面的自動完成。

Demo 想讓 IT 看到的重點：經辦幾乎不用打字，而且每個 skill 能做什麼是**寫在程式裡的護欄**，不是只靠 prompt。

## 2. 目標、非目標、反目標

**目標**

- 對話框輸入「/」跳出 skill 選單（名稱、用途、參數說明），方向鍵或滑鼠選取。
- `/待審案件`、`/警示卡片` 列出案件清單，**點選即送出**對應的 skill 指令。
- `/核卡初審`、`/刷卡風險分析` 後面輸入姓名片段、編號片段或卡號末四碼，跳出候選清單，選取後填入正確編號。
- 處理過的案件**留在清單上並顯示處理結果**。
- 每個 skill 只拿得到自己的工具，在 agent-server 程式層強制執行。

**非目標**

- 不做 AI 端的模糊搜尋（使用者討論時的方案 B）。經辦打姓名但沒有從清單選取時，系統會請他從清單選，不交給 AI 去猜。
- 不做營業額、熱門卡別等統計類問答。
- 不做真實的派件、權限或登入機制。

**反目標（就算功能能動，發生這些事也算失敗）**

- AI 在對象尚未確定為單一客戶編號或卡號前，就執行寫入型工具。
- `/規則問答` 能觸發任何寫入型工具。
- 畫面上的 skill 名稱與 SOP 檔案中的名稱不一致。
- 現有的自由提問、追問、紅線升級、護欄行為退化。

## 3. 限制

- 遵守 [CLAUDE.md](../../../CLAUDE.md) 與 [STYLE.md](../../../STYLE.md)：
  - Java 25 / Spring Boot 4.1.2 / Spring AI 2.0.2。
  - 設定檔只用 `application.properties`。
  - SQL 一律以 `JdbcClient` 明確撰寫。
  - 前端使用純 HTML + Tailwind CDN + 原生 `fetch`。
- agent-server 不連資料庫。所有案件資料都由 mcp-server 提供。
- 新增的清單與搜尋 API **不得註冊為 `@Tool`**，與主管 API 相同，只給前端使用。
- 不改 `schema.sql`。所有狀態從既有資料表推導。
- 現有 `data.sql` 與整合測試的 demo 情境不得改變結果。
- 依 CLAUDE.md 第三節採 TDD：每一步先看到測試失敗，才寫實作。

## 4. 考慮過的方案

| 方案 | 做法 | 取捨 |
|---|---|---|
| 一、只改 prompt 與前端 | 選單寫死在 HTML；`/待審案件` 交給 AI 呼叫新的唯讀工具，再以文字回覆 | 改動最少；但清單是純文字無法點選，與「幾乎不打字」衝突；每次列清單都要等 LLM；skill 名稱在 HTML 與 SOP 兩處維護 |
| **二、skill 檔案為單一來源，清單走 REST（採用）** | 每個 skill 一份 md 檔，agent-server 提供 skill 目錄 API 給選單，並依 skill 限縮工具；清單與自動完成走 mcp-server 唯讀 REST | 名稱只有一個來源；清單可點、快速、結果穩定；工具限縮由程式強制。代價是改動最多 |
| 三、方案二的半套 | 清單走 REST，但不做 skill 路由，整份 SOP 加全部工具一起給 AI | 後端改動較少；但 `/規則問答` 仍碰得到寫入工具，護欄只剩 prompt，違反「Tool 層必須強制把關」的原則 |

使用者選擇**方案二**。

## 5. 使用者已決定的事

1. 入口採**斜線指令**：輸入「/」跳出 skill 選單。
2. 找對象採**待辦清單 + 自動完成**；經辦幾乎不用打字。
3. Skill 清單：

   | 指令 | 種類 | 參數 | 說明 |
   |---|---|---|---|
   | `/待審案件` | 清單 | 無 | 列出新戶申請與目前狀態 |
   | `/警示卡片` | 清單 | 無 | 列出需處理的通報卡片與目前狀態 |
   | `/核卡初審` | AI skill | 客戶編號（自動完成） | 場景一 |
   | `/刷卡風險分析` | AI skill | 卡號（自動完成） | 場景二 |
   | `/規則問答` | AI skill | 自由文字問題 | 只依 SOP 回答，不碰資料 |

4. 點清單上的名字**直接送出**對應指令。
5. 處理過的案件**留在清單上並顯示處理結果**。
6. 不打斜線也照樣能自由提問、追問，行為與現在相同。
7. 對象尚未確定為單一編號前，AI 不得執行任何寫入動作。

## 6. 設計

### 6.1 Skill 檔案（單一來源）

把 `agent-server/src/main/resources/skills/credit_card_sop.md` 拆成：

```
skills/
├── common.md            # 身分、一般問答、權限與護欄、回覆格式（所有對話都載入）
├── 核卡初審.md           # 場景一的規則與流程
├── 刷卡風險分析.md        # 場景二的規則與流程
└── 規則問答.md           # 只依 SOP 回答、不得呼叫工具
```

每個 skill 檔開頭用 `---` 包住的 frontmatter，只放平面的 `key: value`：

```
---
name: 核卡初審
description: 依規則評估新戶申請人，建立初審草稿單或記錄婉拒
argument: customerId
tools: getApplicantProfile, createApprovalDraft, declineApplication
---
```

- `argument` 允許值：`customerId`、`cardNumber`、`text`。
- `tools` 必須是 `ToolAllowList.ALLOWED_TOOL_NAMES` 的子集。啟動時檢查，不符合就啟動失敗。
- 檔案中的 SOP 標題直接使用 skill 名稱，例如 `## 核卡初審`。這樣 prompt 與畫面用的是同一個名稱。

`/規則問答` 的 `tools` 為空，內容是「只依下方規則回答」，並在組 prompt 時附上 `核卡初審.md`、`刷卡風險分析.md` 的規則段落當作參考，讓它答得出門檻問題，但拿不到任何工具。

`/待審案件`、`/警示卡片` 是純前端清單，不經過 LLM，所以**不寫 md 檔**，在 agent-server 的 skill 目錄中以程式登錄（見 6.2）。

### 6.2 agent-server

**Skill 目錄 `SkillCatalog`**

- 啟動時讀取 `classpath:skills/*.md`（排除 `common.md`），解析 frontmatter 與內文。
- 另外登錄兩個清單指令：`待審案件`（來源 `/api/clerk/applications`）、`警示卡片`（來源 `/api/clerk/alert-cards`）。
- 啟動檢查：名稱不可重複、`tools` 必須在 allow list 內、`argument` 必須是允許值。

**`GET /api/agent/skills`**

回傳選單需要的資料：

```json
[
  {"name": "待審案件", "kind": "LIST", "description": "...", "argument": null, "source": "/api/clerk/applications"},
  {"name": "核卡初審", "kind": "AGENT", "description": "...", "argument": "customerId", "source": null}
]
```

不回傳 SOP 內文與工具清單。

**`POST /api/agent/chat` 的解析流程**

請求格式不變，`{conversationId, message}`。agent-server 解析 `message`：

1. 不是以「/」開頭：維持現狀。system prompt 為 `common.md` 加上 `核卡初審.md`、`刷卡風險分析.md` 內文，工具為 allow list 全部 6 個。不載入 `規則問答.md`，因為其中「不得呼叫工具」的指示會與這個模式衝突；一般問答規則已經寫在 `common.md`。
2. 以「/」開頭：取第一個空白前的文字當 skill 名稱，其餘當參數。
   - 名稱不存在：**不呼叫 LLM**，直接回覆「查無此指令，請輸入 / 查看可用指令」。
   - 名稱是清單指令：不呼叫 LLM，回覆「此指令會顯示清單，請從選單選取」。正常情況下前端不會把清單指令送到這裡。
   - `argument` 為 `customerId` 或 `cardNumber` 時，第一個參數必須符合格式：
     - 客戶編號：`^[A-Z]\d{9}$`
     - 卡號：`^\d{16}$`

     不符合時**不呼叫 LLM**，回覆「請從候選清單選取對象」。這是「對象未確定前不得寫入」的程式層保證。
   - 驗證通過：system prompt 為 `common.md` 加上該 skill 內文，工具只給該 skill 的 `tools`（`/規則問答` 為零個）。user message 原文照送，包含斜線與參數。
3. 對話記憶沿用同一個 `conversationId`。下一句沒有斜線的追問，回到情況 1 的完整 prompt 與全部工具，可以沿用先前的編號。

**對 `CreditCardAgentConfiguration` 的影響**

`defaultSystem` 改為每次請求以 `.system(...)` 指定，工具也改為每次請求依 skill 篩選。`ToolAllowList` 保留為最外層上限。

### 6.3 mcp-server：經辦清單與搜尋 REST

新增 package `com.demo.creditcard.mcp.clerk`，提供唯讀 REST API，**不註冊為 `@Tool`**，也不寫稽核紀錄（唯讀）。

| Method | Path | 用途 |
|---|---|---|
| GET | `/api/clerk/applications` | 新戶申請清單與狀態 |
| GET | `/api/clerk/alert-cards` | 警示卡片清單與狀態 |
| GET | `/api/clerk/applicants/search?q=` | `/核卡初審` 自動完成 |
| GET | `/api/clerk/cards/search?q=` | `/刷卡風險分析` 自動完成 |

CORS：`/api/clerk/**` 與 `/api/supervisor/**` 一樣，只允許 `http://localhost:8080`，方法只開 `GET`。

**申請人的定義**：名下沒有任何 `credit_cards` 的客戶，或者在 `approval_drafts` 中有紀錄的客戶。後者是為了讓主管放行、已發卡的申請人留在清單上。

**申請狀態**，依序判斷，命中即停：

| 狀態代碼 | 顯示文字 | 條件 |
|---|---|---|
| `APPROVED` | 已核卡 | 最新一筆 `approval_drafts.status = 'APPROVED'` |
| `PENDING_REVIEW` | 待主管放行 | 最新一筆草稿為 `PENDING_REVIEW` |
| `SUPERVISOR_REJECTED` | 主管退回 | 最新一筆草稿為 `REJECTED` |
| `DECLINED` | 已婉拒 | `audit_logs` 有 `action_type = 'APPLICATION_DECLINE'` 且 `target_id = customer_id` |
| `AWAITING_REVIEW` | 待初審 | 以上皆無 |

「最新一筆」以 `draft_id` 最大者為準。`GUARDRAIL_BLOCKED` 不改變狀態。

清單每筆回傳：`customerId`、`fullName`、`status`、`statusLabel`、`actionable`。只有 `AWAITING_REVIEW` 與 `SUPERVISOR_REJECTED` 的 `actionable` 為 `true`。

**警示卡片的定義**：對每張卡套用既有的 `FraudControlService` 規則引擎評估，符合下列任一條件就列入：

- 判定不是 `NO_ANOMALY`。
- 卡片狀態不是 `ACTIVE`。
- `audit_logs` 有該卡號的 `ESCALATION` 紀錄。

**卡片狀態**，依序判斷，命中即停：

| 狀態代碼 | 顯示文字 | 條件 |
|---|---|---|
| `FROZEN` | 已凍結 | `credit_cards.status = 'TEMP_FROZEN'` |
| `ESCALATED` | 已升級風控 | 有 `ESCALATION` 稽核紀錄 |
| `AWAITING_ACTION` | 待處理（附規則引擎判定） | 以上皆無 |

清單每筆回傳：`cardNumber`、`last4`、`holderName`、`verdict`、`verdictLabel`、`status`、`statusLabel`、`actionable`。只有 `AWAITING_ACTION` 的 `actionable` 為 `true`。

依現有 demo 資料，啟動後 `/警示卡片` 應列出 0001～0005 五張，0006 不列。

**搜尋規則**

- `q` 去除前後空白後不可為空。空白時回傳空陣列。
- 申請人搜尋範圍：上面定義的申請人。符合下列任一條件即命中：
  - `full_name` 包含 `q`。
  - `customer_id` 包含 `q`（不分大小寫）。
- 卡片搜尋範圍：所有卡片。符合下列任一條件即命中：
  - 持卡人 `full_name` 包含 `q`。
  - `card_number` 以 `q` 結尾（卡號末碼）。
- 最多回傳 10 筆，依編號排序。每筆附上狀態文字，讓經辦挑選時看得到。
- 一律使用參數化 SQL（`LIKE :pattern`），不可字串拼接。

### 6.4 前端 `index.html`

- **移除**左側「場景一、場景二」兩張下拉卡片。
- **新增**兩顆快捷按鈕：「待審案件」、「警示卡片」，效果等同輸入對應的斜線指令。
- 「自由提問」區改名為「對話」，`placeholder` 提示「輸入 / 使用指令」。

**斜線選單**

- 輸入框內容以「/」開頭、且尚未出現空白時，顯示 `/api/agent/skills` 的完整清單。可依已輸入的文字前綴篩選。
- 支援 ↑ ↓ 移動選取、Enter 或滑鼠點選確認、Esc 關閉。
- 選取清單指令：立即呼叫對應的 `source` API，把清單渲染在對話區。
- 選取 AI skill：把 `/名稱 ` 填入輸入框，等待輸入參數。

**自動完成**

- `argument` 為 `customerId` 或 `cardNumber` 時，經辦在參數位置輸入文字，停頓 250ms 後呼叫對應的搜尋 API，顯示候選清單。
- 選取後輸入框變成 `/核卡初審 A100000003`，然後**直接送出**。

**清單渲染**

- 由前端渲染，不經過 LLM。每列顯示名稱、編號或卡號末四碼、狀態文字。
- `actionable` 為 `true` 的列可點，點下去直接送出 `/核卡初審 {customerId}` 或 `/刷卡風險分析 {cardNumber}`。不可點的列以灰色呈現狀態。
- 每次執行清單指令都重新呼叫 API，確保顯示最新狀態。

AI 回覆仍是純文字，渲染方式維持現狀。

### 6.5 文件更新

- 更新 `CLAUDE.md`：
  - 第二節目錄結構（`skills/` 改為多檔）。
  - 第五節：新增經辦 REST API 表格，以及 agent-server 的 `/api/agent/skills`。
  - 第八節：新增「斜線指令與 skill 限縮工具」的規範。
- `spec.md` 不動。

## 7. 測試（TDD，先紅後綠）

**mcp-server（Testcontainers 整合測試，使用 `data.sql`）**

- `/api/clerk/applications`
  - 啟動後 7 位申請人皆為 `AWAITING_REVIEW`。
  - 建立草稿後變 `PENDING_REVIEW`。
  - 主管核准後變 `APPROVED`，而且仍在清單中。
  - 主管退回後變 `SUPERVISOR_REJECTED` 且可點。
  - 婉拒後變 `DECLINED`。
- `/api/clerk/alert-cards`
  - 列出 0001～0005，不含 0006，判定分別符合第七節預期結果。
  - 凍結 0002 後變 `FROZEN`。
  - 升級 0005 後變 `ESCALATED`。
- 搜尋
  - `q=林` 申請人只回傳林淑芬，不含持卡人林小旅；卡片只回傳 0001 林小旅。
  - `q=0002` 卡片命中 0002。
  - `q=` 空白回傳空陣列。
  - `q` 含 `%` 或 `'` 時不出錯，並以字面比對。
- 註冊測試：clerk API 的方法沒有出現在 MCP 工具清單中。

**agent-server**

- `SkillCatalog`
  - 載入 3 個 AI skill 加 2 個清單指令。
  - 名稱重複、`tools` 不在 allow list 內、`argument` 不合法時啟動失敗（用測試專用 resource 驗證）。
- `/api/agent/skills`：回傳 5 筆，不含 SOP 內文。
- chat 解析
  - `/核卡初審 A100000003`：只送出 3 個場景一工具，system prompt 含「核卡初審」段落。
  - `/刷卡風險分析 4000000000000002`：只送出 3 個場景二工具。
  - `/規則問答 御璽卡門檻？`：送出 0 個工具。
  - `/核卡初審 陳`：不呼叫 LLM（`RecordingChatModel` 沒有收到請求），回覆請從清單選取。
  - `/不存在`：不呼叫 LLM。
  - 沒有斜線的訊息：6 個工具加完整 prompt，與現狀相同。
- 既有測試全部維持綠燈。

前端沒有自動化測試，以手動 demo 腳本驗證（見第 9 節）。

## 8. 我的決定（請使用者確認）

| # | 決定 | 理由 |
|---|---|---|
| 1 | 移除左側兩張場景下拉卡片，改為兩顆快捷按鈕 | 待辦清單已涵蓋其功能，保留會出現兩種入口 |
| 2 | skill 名稱用中文；選單以方向鍵或點選為主 | 中文輸入法不方便在「/」後即時篩選 |
| 3 | skill 中繼資料用 md frontmatter（平面 `key: value`，自行解析，不引入 YAML 函式庫） | 名稱與規則放在同一個檔案才是單一來源；這不是應用程式設定檔，不違反「設定檔禁止 YAML」 |
| 4 | 參數格式不符時由 agent-server 直接回覆、不呼叫 LLM | 「對象未確定不得寫入」用程式保證，而且不浪費 LLM 呼叫 |
| 5 | 清單與搜尋 API 放 mcp-server，前端以 CORS 直接呼叫 | 與 `supervisor.html` 呼叫主管 API 的既有模式一致；agent-server 不連資料庫 |
| 6 | 「申請人」定義為無卡客戶或有草稿紀錄者，不改 schema | 現有資料表足以推導；不動 `schema.sql` 降低對既有測試的影響 |
| 7 | 主管退回的申請可再次點選初審 | `createApprovalDraft` 只擋「已有待審草稿」，退回後允許重新評估 |
| 8 | 0001（出國旅遊正常）列為警示卡片，狀態維持「待處理」 | 這張卡的預期動作是「建議解除警報」，不會有寫入紀錄；列出來才能 demo 這條規則 |
| 9 | 警示判定對每張卡逐一套用規則引擎 | demo 只有 6 張卡；正式環境需改為批次或預先計算 |
| 10 | 斜線指令後的追問（沒有斜線）回到完整 prompt 與全部工具 | 維持現有追問行為；寫入仍受 SOP「明確要求」與 Tool 層護欄約束 |

## 9. 手動 Demo 驗收腳本

1. 啟動三個服務，開啟 `http://localhost:8080/`。
2. 點「待審案件」，應看到 7 位都是「待初審」。點「陳建宏」，AI 建立 `INFINITE` / 500,000 草稿。
3. 再點「待審案件」，陳建宏顯示「待主管放行」且不可點。
4. 到 `supervisor.html` 核准，回來再看清單，陳建宏顯示「已核卡」。
5. 輸入 `/刷卡風險分析 林`，候選清單只出現「林小旅 0001」，選取後 AI 判定出國旅遊正常、不鎖卡。
6. 點「警示卡片」，點 0005，回覆開頭出現【緊急警告】並升級風控。再看清單，0005 顯示「已升級風控」。
7. 輸入 `/規則問答 御璽卡門檻是多少？`，AI 依 SOP 回答，不呼叫任何工具。
8. 輸入 `/核卡初審 陳` 直接按 Enter 送出（不選候選），系統回覆請從清單選取，沒有呼叫 AI。
9. 不打斜線輸入「那它為什麼被凍結？」，追問行為與現在相同。

## 10. 留給實作者的事

- `SkillCatalog` 的 frontmatter 解析細節（空白處理、錯誤訊息文字）。
- 申請狀態與警示卡片的 SQL 寫法（單一查詢或多查詢皆可，但必須寫在 Repository 內）。
- 斜線選單與自動完成的 Tailwind 樣式與 DOM 結構。
- `common.md` 從現有 SOP 拆分的確切切點。內容不得刪減現有規則。
