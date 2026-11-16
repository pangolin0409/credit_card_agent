# 經辦斜線指令 Skill 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓經辦用「/」叫出有名稱的 skill、從可點的待辦清單與自動完成選取對象，並由 agent-server 依 skill 限縮 AI 可用的工具。

**Architecture:**
- mcp-server 新增 `clerk` 功能套件，提供唯讀 REST（不是 `@Tool`），內容包括申請清單、警示卡片清單，以及兩支搜尋。
- agent-server 新增 `skill` 套件，把 SOP 拆成多個 skill md 檔，由 `SkillCatalog` 統一管理。`SkillRouter` 解析斜線指令，決定這次對話的 system prompt 與工具，或者不呼叫 LLM、直接回覆。
- 前端 `index.html` 改成斜線選單、自動完成，以及可點選的清單。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring AI 2.0.2、JdbcClient、PostgreSQL（Testcontainers）、JUnit 5 + AssertJ、HTML + Tailwind CDN + 原生 JS。

**Spec:** [docs/superpowers/specs/2026-11-16-clerk-slash-skills-design.md](../specs/2026-11-16-clerk-slash-skills-design.md)

## Global Constraints

- 遵守 [STYLE.md](../../../STYLE.md)，包括以下規則：
  - 不寫註解。
  - 建構子注入。
  - DTO 用 record，代碼值用 enum。
  - Repository 每個方法一條 SQL，使用 text block 與具名參數。
  - 測試命名 `should…When…`，並以 `@DisplayName` 寫中文業務規則。
- 不改 `schema.sql`、`data.sql`。
- clerk API 不得加 `@Tool`；`McpToolRegistrationTest`（恰好 6 個工具）必須維持綠燈。
- clerk API 的 CORS 只允許 `http://localhost:8080`，方法只開 `GET`。
- skill 的 `tools` 必須是 `ToolAllowList.ALLOWED_TOOL_NAMES` 的子集；違反時應用程式啟動失敗。
- 直接回覆的文字固定如下，逐字照抄：
  - 查無指令：`查無此指令，請輸入 / 查看可用指令`
  - 清單指令送到 chat：`此指令會顯示清單，請從選單選取`
  - 對象格式不符：`請從候選清單選取對象`
  - 問題空白：`請在指令後輸入問題`
- 參數格式：客戶編號 `^[A-Z]\d{9}$`，卡號 `^\d{16}$`。
- 搜尋最多回傳 10 筆，依編號排序；`q` 空白回傳空陣列。
- TDD：每個實作步驟前都要先看到測試失敗。

## Review Focus

1. 中文輸入法打出全形「／」與全形空白「　」時，仍視為斜線指令。由 Task 5 的 `SlashCommandTest` 釘住。
2. 搜尋字串含 `%`、`_`、`\`、`'` 時，以字面比對，不報錯也不變成萬用字元。由 Task 3 釘住。
3. `/規則問答` 後面沒有問題時，回覆 `請在指令後輸入問題`，不呼叫 LLM。由 Task 5 釘住。
4. 主管核准後，申請人名下已經有卡，但在申請清單與申請人搜尋中仍要出現，狀態為「已核卡」。由 Task 2、Task 3 釘住。
5. 只輸入「/」就送出時，回覆查無指令，不呼叫 LLM。由 Task 5 釘住。

## File Structure

**mcp-server**（`mcp-server/src/main/java/com/demo/creditcard/mcp/`）

| 檔案 | 職責 |
|---|---|
| `fraudcontrol/FraudVerdict.java`（改） | 每個判定加上中文 `label()` |
| `clerk/ApplicationStatus.java` | 申請狀態 enum，含 `label()`、`isActionable()` |
| `clerk/CardHandlingStatus.java` | 卡片處理狀態 enum，含 `label()`、`isActionable()` |
| `clerk/ApplicantSnapshot.java`、`clerk/CardSnapshot.java` | 從資料庫讀出的資料列 |
| `clerk/ClerkStatusPolicy.java` | 純函式：推導狀態、判斷是否為警示卡 |
| `clerk/ApplicantListItem.java`、`clerk/CardListItem.java` | API 回應 |
| `clerk/LikePatterns.java` | LIKE 字串跳脫 |
| `clerk/ClerkQueryRepository.java` | 清單與搜尋 SQL |
| `clerk/ClerkQueryService.java` | 組合 Repository、Policy、`FraudControlService` |
| `clerk/ClerkQueryController.java` | `/api/clerk/**` |
| `shared/WebCorsConfiguration.java`（新）、`supervisor/SupervisorCorsConfiguration.java`（刪） | 合併 CORS 設定 |

**agent-server**（`agent-server/src/main/java/com/demo/creditcard/agent/`）

| 檔案 | 職責 |
|---|---|
| `skill/SkillKind.java`、`skill/SkillArgument.java` | skill 種類與參數規則 |
| `skill/SkillDefinition.java` | 一個 skill 的完整定義 |
| `skill/SkillFileParser.java` | 解析 md frontmatter |
| `skill/SkillCatalog.java` | 全部 skill 加上 common，含啟動驗證 |
| `skill/SkillCatalogConfiguration.java` | 組出 `SkillCatalog` bean |
| `skill/SkillSummary.java`、`skill/SkillController.java` | `GET /api/agent/skills` |
| `skill/SlashCommand.java` | 解析斜線指令 |
| `skill/RoutingDecision.java`、`skill/SkillRouter.java` | 決定 prompt 與工具，或直接回覆 |
| `ToolAllowList.java`（改） | 依 skill 篩工具 |
| `CreditCardAgentService.java`、`CreditCardAgentConfiguration.java`（改） | 接上 router，移除 default system |
| `resources/skills/common.md`、`card-application-review.md`、`card-risk-analysis.md`、`rule-inquiry.md`（新）；`credit_card_sop.md`（刪） | 拆分後的 SOP |
| `resources/static/index.html`（改） | 斜線選單、自動完成、清單 |

**計畫層級的決定（偏離設計之處）：**
- skill 檔名改用英文，例如 `card-application-review.md`；中文名稱寫在 frontmatter 的 `name`。理由：STYLE.md 規定「程式命名一律英文」，而且可以避免中文檔名在 jar、git 中的編碼問題。名稱的單一來源仍然是 md 檔。
- frontmatter 多兩個 key：
  - `references`：`/規則問答` 用來附上兩個場景的規則。
  - `free-chat: true`：標記自由提問模式要載入哪些 skill。這樣就不必在程式裡寫死檔名。

---

### Task 1：mcp-server 狀態規則（純 Policy）

**Files:**
- Modify: `mcp-server/src/main/java/com/demo/creditcard/mcp/fraudcontrol/FraudVerdict.java`
- Create: `clerk/ApplicationStatus.java`、`clerk/CardHandlingStatus.java`、`clerk/ApplicantSnapshot.java`、`clerk/CardSnapshot.java`、`clerk/ClerkStatusPolicy.java`
- Test: `mcp-server/src/test/java/com/demo/creditcard/mcp/clerk/ClerkStatusPolicyTest.java`

**Interfaces:**
- Produces:
  - `FraudVerdict#label()`，對應值：
    - `ESCALATE_TO_RISK_OFFICER`：`超高金額紅線，需升級風控`
    - `NORMAL_TRAVEL`：`出國旅遊正常，建議解除警報`
    - `OVERSEAS_FRAUD`：`疑似境外盜刷`
    - `CARD_TESTING`：`疑似小額測卡`
    - `NO_ANOMALY`：`無異常`
  - `enum ApplicationStatus`，各值的 `label()` 與 `isActionable()`：
    - `APPROVED`：`已核卡`，false
    - `PENDING_REVIEW`：`待主管放行`，false
    - `SUPERVISOR_REJECTED`：`主管退回`，true
    - `DECLINED`：`已婉拒`，false
    - `AWAITING_REVIEW`：`待初審`，true
  - `enum CardHandlingStatus`，各值的 `label()` 與 `isActionable()`：
    - `FROZEN`：`已凍結`，false
    - `ESCALATED`：`已升級風控`，false
    - `AWAITING_ACTION`：`待處理`，true
  - `record ApplicantSnapshot(String customerId, String fullName, Optional<DraftStatus> latestDraftStatus, boolean hasDecline)`
  - `record CardSnapshot(String cardNumber, String holderName, CardStatus status, boolean hasEscalation)`
  - `@Component ClerkStatusPolicy`：
    - `ApplicationStatus resolveApplicationStatus(ApplicantSnapshot applicant)`
    - `CardHandlingStatus resolveCardHandlingStatus(CardSnapshot card)`
    - `boolean isAlertCard(CardSnapshot card, FraudVerdict verdict)`

- [ ] **Step 1：寫失敗測試** `ClerkStatusPolicyTest`。每個案例一個方法，涵蓋：
  - 草稿 `APPROVED` 而且 `hasDecline=true` 時，結果為 `APPROVED`（草稿優先）。
  - `PENDING_REVIEW`、`REJECTED` 分別對應 `PENDING_REVIEW`、`SUPERVISOR_REJECTED`。
  - 沒有草稿但 `hasDecline=true` 時為 `DECLINED`；兩者皆無時為 `AWAITING_REVIEW`。
  - `TEMP_FROZEN` 而且 `hasEscalation=true` 時為 `FROZEN`（凍結優先）；`ACTIVE` 加上 `hasEscalation=true` 為 `ESCALATED`；`ACTIVE` 加上 `false` 為 `AWAITING_ACTION`。
  - `isAlertCard`：
    - `ACTIVE`、無升級、`NO_ANOMALY`：false。
    - `ACTIVE`、無升級、`NORMAL_TRAVEL`：true。
    - `TEMP_FROZEN`、`NO_ANOMALY`：true。
    - `ACTIVE`、`hasEscalation=true`、`NO_ANOMALY`：true。
  - `AWAITING_REVIEW.isActionable()` 與 `SUPERVISOR_REJECTED.isActionable()` 為 true，其他皆為 false。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl mcp-server test -Dtest=ClerkStatusPolicyTest`
  Expected: 編譯失敗（找不到 `ClerkStatusPolicy`）。

- [ ] **Step 3：實作** 上列 enum、record 與 `ClerkStatusPolicy`。判斷順序照設計文件 6.3 的兩張表，命中即停。

- [ ] **Step 4：執行並確認通過**
  Run: `./mvnw -pl mcp-server test -Dtest=ClerkStatusPolicyTest`
  Expected: PASS

- [ ] **Step 5：Commit** `feat: add clerk status policy for applications and alert cards`

---

### Task 2：申請清單與警示卡片 REST、CORS

**Files:**
- Create: `clerk/ApplicantListItem.java`、`clerk/CardListItem.java`、`clerk/ClerkQueryRepository.java`、`clerk/ClerkQueryService.java`、`clerk/ClerkQueryController.java`、`shared/WebCorsConfiguration.java`
- Delete: `supervisor/SupervisorCorsConfiguration.java`
- Test: `mcp-server/src/test/java/com/demo/creditcard/mcp/clerk/ClerkQueryControllerIntegrationTest.java`

**Interfaces:**
- Consumes：Task 1 全部型別；`FraudControlService#getCardRiskProfile(String) -> CardRiskProfile`（取 `.assessment().verdict()`）。
- Produces:
  - `record ApplicantListItem(String customerId, String fullName, ApplicationStatus status, String statusLabel, boolean actionable)`，以及 `static ApplicantListItem of(ApplicantSnapshot applicant, ApplicationStatus status)`。
  - `record CardListItem(String cardNumber, String last4, String holderName, FraudVerdict verdict, String verdictLabel, CardHandlingStatus status, String statusLabel, boolean actionable)`，以及 `static CardListItem of(CardSnapshot card, FraudVerdict verdict, CardHandlingStatus status)`。
  - `ClerkQueryRepository`：
    - `List<ApplicantSnapshot> findApplicants()`
    - `List<CardSnapshot> findCards()`
  - `ClerkQueryService`：
    - `List<ApplicantListItem> listApplications()`
    - `List<CardListItem> listAlertCards()`
  - `GET /api/clerk/applications`、`GET /api/clerk/alert-cards`

- [ ] **Step 1：寫失敗測試** `ClerkQueryControllerIntegrationTest`。寫法比照 `SupervisorApprovalControllerIntegrationTest`：使用 `@SpringBootTest`、`@AutoConfigureMockMvc`、`@Import(TestcontainersConfiguration.class)`、`@Transactional`。測試內容：
  - `shouldListAllSevenApplicantsAwaitingReviewWhenFreshlyStarted`：
    - 回傳 7 筆，`$[0].customerId` 為 `A100000001`，`$[*].status` 全部是 `AWAITING_REVIEW`。
    - `$[0].statusLabel` 為 `待初審`，`$[0].actionable` 為 true。
    - 不包含任何 `B2` 開頭的編號。
  - `shouldShowPendingReviewWhenDraftCreated`：用 `CardApplicationTools.createApprovalDraft(new ApprovalDraftRequest("A100000003", CardTier.INFINITE, 500_000, "符合無限卡"))` 建草稿後，A100000003 的狀態為 `PENDING_REVIEW`，`actionable` 為 false。
  - `shouldKeepApprovedApplicantInListWhenSupervisorApproves`：
    - 先建草稿，再以 MockMvc POST `/api/supervisor/approval-drafts/{id}/approve`。
    - 之後清單仍是 7 筆，A100000003 為 `APPROVED`，`statusLabel` 為 `已核卡`。
  - `shouldAllowReviewAgainWhenSupervisorRejects`：主管退回後為 `SUPERVISOR_REJECTED`，`actionable` 為 true。
  - `shouldShowDeclinedWhenApplicationDeclined`：用 `declineApplication("A100000007", "未達門檻")` 婉拒後為 `DECLINED`。
  - `shouldListFiveAlertCardsExcludingNormalCard`：
    - 回傳 5 筆，卡號依序為 0001 到 0005，不含 `4000000000000006`。
    - 各卡的 verdict 依序為：
      - 0001：`NORMAL_TRAVEL`
      - 0002：`OVERSEAS_FRAUD`
      - 0003：`OVERSEAS_FRAUD`
      - 0004：`CARD_TESTING`
      - 0005：`ESCALATE_TO_RISK_OFFICER`
    - `$[0].last4` 為 `0001`，`$[0].holderName` 為 `林小旅`，`$[0].verdictLabel` 為 `出國旅遊正常，建議解除警報`。
  - `shouldShowFrozenWhenCardFrozen`：用 `FraudControlTools.freezeCard("4000000000000002", "境外盜刷")` 凍結後，0002 為 `FROZEN`，`actionable` 為 false。
  - `shouldShowEscalatedWhenCaseEscalated`：用 `escalateToRiskOfficer("4000000000000005", "紅線")` 升級後，0005 為 `ESCALATED`。
  - `shouldAllowCorsGetFromAgentServer`：
    - 對 `/api/clerk/applications` 送 OPTIONS，帶 `Origin: http://localhost:8080`、`Access-Control-Request-Method: GET`。
    - 回應的 `Access-Control-Allow-Origin` 為 `http://localhost:8080`。
  - `shouldRejectCorsPostOnClerkApi`：同上，但改成 `Access-Control-Request-Method: POST`，預期 status 403。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl mcp-server test -Dtest=ClerkQueryControllerIntegrationTest`
  Expected: FAIL（404 或編譯失敗）。

- [ ] **Step 3：實作 Repository**
  - `findApplicants` 用一條 SQL 完成：
    - 篩選條件：沒有 `credit_cards` 的客戶，或者有 `approval_drafts` 的客戶。
    - 用相關子查詢取最新草稿狀態：`ORDER BY draft_id DESC LIMIT 1`。
    - 用 `EXISTS` 判斷 `audit_logs` 中是否有 `action_type = 'APPLICATION_DECLINE' AND target_id = customer_id`。
    - `ORDER BY customer_id`。
  - `findCards`：`credit_cards` JOIN `customers`，用 `EXISTS` 判斷 `ESCALATION` 稽核紀錄（`target_id = card_number`），`ORDER BY card_number`。

- [ ] **Step 4：實作 Service 與 Controller**
  - Service 呼叫 Policy 推導狀態；`listAlertCards` 對每張卡呼叫 `FraudControlService#getCardRiskProfile` 取得 verdict，再以 `isAlertCard` 過濾。
  - Controller 只委派給 Service。

- [ ] **Step 5：CORS**
  - 新增 `shared/WebCorsConfiguration`：
    - `/api/supervisor/**` 允許 `GET`、`POST`。
    - `/api/clerk/**` 只允許 `GET`。
    - 兩者 origin 都是 `http://localhost:8080`。
  - 刪除 `SupervisorCorsConfiguration`。

- [ ] **Step 6：執行 mcp-server 全部測試**
  Run: `./mvnw -pl mcp-server test`
  Expected: 全部 PASS，包含既有的 supervisor CORS 測試與 `McpToolRegistrationTest`。

- [ ] **Step 7：Commit** `feat: add clerk application and alert card list API`

---

### Task 3：自動完成搜尋 REST

**Files:**
- Create: `clerk/LikePatterns.java`
- Modify: `clerk/ClerkQueryRepository.java`、`clerk/ClerkQueryService.java`、`clerk/ClerkQueryController.java`
- Test: `clerk/ClerkSearchIntegrationTest.java`、`clerk/LikePatternsTest.java`

**Interfaces:**
- Consumes：Task 2 的 `ApplicantListItem`、`CardListItem`、Repository、Service。
- Produces:
  - `LikePatterns`：
    - `static String contains(String keyword)`：回傳 `%keyword%`。
    - `static String endsWith(String keyword)`：回傳 `%keyword`。
    - 兩者都要把 `\`、`%`、`_` 前面加上 `\`。
  - `ClerkQueryRepository`：
    - `List<ApplicantSnapshot> searchApplicants(String containsPattern, int limit)`
    - `List<CardSnapshot> searchCards(String containsPattern, String suffixPattern, int limit)`
  - `ClerkQueryService`：
    - `List<ApplicantListItem> searchApplicants(String keyword)`
    - `List<CardListItem> searchCards(String keyword)`
    - 常數 `SEARCH_RESULT_LIMIT = 10`
  - `GET /api/clerk/applicants/search?q=`、`GET /api/clerk/cards/search?q=`（`q` 的 `defaultValue` 為 `""`）

- [ ] **Step 1：寫失敗測試**
  - `LikePatternsTest`：
    - `contains("50%_\\")` 的結果為 `"%50\\%\\_\\\\%"`（兩者都是 Java 字面量；實際字串分別為 `50%_\` 與 `%50\%\_\\%`）。
    - `endsWith("0002")` 的結果為 `%0002`。
  - `ClerkSearchIntegrationTest`（`@Transactional`）：
    - `shouldFindOnlyApplicantNotCardholderWhenSearchingSurname`：`q=林` 的申請人結果只有 `A100000002 林淑芬`。
    - `shouldMatchCustomerIdIgnoringCase`：`q=a1000000` 回傳 7 筆。
    - `shouldFindCardByHolderName`：`q=林` 的卡片結果只有 `4000000000000001`，`holderName` 為 `林小旅`。
    - `shouldFindCardByLastDigits`：`q=0002` 的卡片結果只有 0002。
    - `shouldIncludeNormalCardInCardSearch`：`q=0006` 回傳 0006，`verdict` 為 `NO_ANOMALY`。
    - `shouldReturnEmptyWhenKeywordBlank`：`q=`（空白）與 `q=%20` 兩支 API 都回傳 `[]`。
    - `shouldTreatWildcardsLiterally`：`q=%`、`q=_`、`q='` 兩支 API 都回 200 且結果為 `[]`。
    - `shouldStillFindApplicantAfterApproval`：
      - 建草稿並由主管核准 A100000004。
      - `q=張雅婷` 的申請人結果有 1 筆，狀態為 `APPROVED`。
      - `q=張雅婷` 的卡片結果出現她的新卡。
    - `shouldLimitResultsToTen`：用 test SQL 插入 11 位姓「測」的無卡客戶，`q=測` 回傳 10 筆。
      - 這段 SQL 放在 `mcp-server/src/test/resources/clerk/eleven-applicants.sql`，以 `@Sql` 載入。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl mcp-server test -Dtest='ClerkSearchIntegrationTest,LikePatternsTest'`
  Expected: FAIL

- [ ] **Step 3：實作**
  - SQL 一律使用 `LIKE :pattern ESCAPE '\'`。
  - 客戶編號比對用 `UPPER(customer_id) LIKE UPPER(:containsPattern)`。
  - 卡號比對用 `card_number LIKE :suffixPattern`。
  - 申請人的範圍定義與 Task 2 相同。
  - 排序、`LIMIT :limit`。
  - Service 在 `keyword.isBlank()` 時直接回傳空 List，不查資料庫；非空時先 `strip()`。

- [ ] **Step 4：執行 mcp-server 全部測試**
  Run: `./mvnw -pl mcp-server test`
  Expected: 全部 PASS

- [ ] **Step 5：Commit** `feat: add clerk search API for autocomplete`

---

### Task 4：Skill 檔案、SkillCatalog 與 `/api/agent/skills`

**Files:**
- Create：`agent-server/src/main/resources/skills/common.md`、`card-application-review.md`、`card-risk-analysis.md`、`rule-inquiry.md`
- Create：`skill/SkillKind.java`、`SkillArgument.java`、`SkillDefinition.java`、`SkillFileParser.java`、`SkillCatalog.java`、`SkillCatalogConfiguration.java`、`SkillSummary.java`、`SkillController.java`
- Test：`agent-server/src/test/java/com/demo/creditcard/agent/skill/SkillFileParserTest.java`、`SkillCatalogTest.java`、`SkillControllerTest.java`
- Test resources：`agent-server/src/test/resources/test-skills/valid.md`、`missing-name.md`
- 這個 task **不刪** `credit_card_sop.md`，Task 6 才切換。

**Interfaces:**
- Consumes：`ToolAllowList.ALLOWED_TOOL_NAMES`。
- Produces:
  - `enum SkillKind { AGENT, LIST }`
  - `enum SkillArgument`：
    - 成員：`CUSTOMER_ID("customerId")`、`CARD_NUMBER("cardNumber")`、`TEXT("text")`、`NONE("none")`。
    - `String key()`
    - `boolean accepts(String argumentText)`：
      - `CUSTOMER_ID`：取第一個 token，比對 `^[A-Z]\d{9}$`。
      - `CARD_NUMBER`：取第一個 token，比對 `^\d{16}$`。
      - `TEXT`：非空白即可。
      - `NONE`：一律 true。
    - `static SkillArgument fromKey(String key)`：未知的 key 拋 `IllegalStateException`。
  - `record SkillDefinition(String name, SkillKind kind, String description, SkillArgument argument, Set<String> toolNames, List<String> references, boolean isIncludedInFreeChat, String instructions, String source)`
    - `static SkillDefinition list(String name, String description, String source)`：建立 LIST skill，參數為 `NONE`，工具與 references 為空，`instructions` 為空字串。
  - `@Component SkillFileParser`：`SkillDefinition parse(Resource resource)`。
    - frontmatter 是平面的 `key: value`。
    - 必填：`name`、`description`、`argument`、`tools`（可以空白）。
    - 選填：`references`（逗號分隔）、`free-chat`（預設 false）。
    - 缺少必填欄位，或者沒有 `---` 區塊時，拋出 `IllegalStateException`，訊息要包含檔名。
  - `SkillCatalog`：
    - 建構子 `SkillCatalog(String commonInstructions, List<SkillDefinition> skills)`，在建構子內驗證：名稱不可重複、`toolNames ⊆ ToolAllowList.ALLOWED_TOOL_NAMES`、`references` 必須指向既有的 AGENT skill。違反時拋出 `IllegalStateException`。
    - `Optional<SkillDefinition> find(String name)`
    - `List<SkillDefinition> all()`：維持建構時的順序。
    - `List<SkillDefinition> freeChatSkills()`
    - `String commonInstructions()`
  - `SkillCatalogConfiguration` bean 的順序：
    1. `list("待審案件", "列出新戶申請與目前處理狀態", "/api/clerk/applications")`
    2. `list("警示卡片", "列出需處理的通報卡片與目前處理狀態", "/api/clerk/alert-cards")`
    3. `card-application-review.md`
    4. `card-risk-analysis.md`
    5. `rule-inquiry.md`

    三份 md 用 `@Value("classpath:skills/…")` 注入。
  - `record SkillSummary(String name, String kind, String description, String argument, String source)`，以及 `static SkillSummary from(SkillDefinition)`：
    - `argument` 為 `NONE` 時輸出 null，否則輸出 `key()`。
    - AGENT skill 的 `source` 為 null。
  - `GET /api/agent/skills` 回傳 `List<SkillSummary>`。

**skill 檔的 frontmatter（逐字使用）：**

```
card-application-review.md:
name: 核卡初審 / description: 依規則評估新戶申請人，建立初審草稿單或記錄婉拒 / argument: customerId
tools: getApplicantProfile, createApprovalDraft, declineApplication / free-chat: true

card-risk-analysis.md:
name: 刷卡風險分析 / description: 分析卡片近期交易，依規則凍結、升級風控或回報無異常 / argument: cardNumber
tools: getCardRiskProfile, freezeCard, escalateToRiskOfficer / free-chat: true

rule-inquiry.md:
name: 規則問答 / description: 詢問 SOP 的門檻、計算方式與流程，不查詢也不處置任何資料 / argument: text
tools: (空白) / references: 核卡初審, 刷卡風險分析
```

（上面的 `/` 只是排版用的分隔，實際檔案中每個 key 各佔一行。）

**內文拆分方式：**
- `common.md`：現有 SOP 的標題、開頭段落、`## 一般問答`、`## 權限與護欄`、`## 回覆格式`。另外新增 `## 斜線指令` 一節，寫明：
  - 「經辦以 /skill 名稱開頭的訊息，代表明確要求執行該 skill；對象已由系統確認。」
- 兩個場景檔：分別放現有的「場景一」「場景二」內容，標題改為 `## 核卡初審`、`## 刷卡風險分析`。
- `rule-inquiry.md`：標題 `## 規則問答`，內容寫明：
  - 只依附上的規則回答，不得聲稱已查詢或處置任何客戶或卡片。
  - 需要實際資料時，請經辦改用 `/核卡初審` 或 `/刷卡風險分析`。
- 現有規則文字不得刪減。

- [ ] **Step 1：寫失敗測試**
  - `SkillFileParserTest`：
    - 解析 `test-skills/valid.md`，名稱、工具集合、`references`、`free-chat`、內文都要正確，而且內文不含 frontmatter。
    - 解析 `missing-name.md` 時，拋出的例外訊息包含 `missing-name.md`。
  - `SkillCatalogTest`：
    - 純單元測試，直接建構 `SkillCatalog`：
      - 名稱重複時拋例外。
      - 工具 `approveApprovalDraft` 不在 allow list，拋例外。
      - `references` 指向不存在的 skill，拋例外。
    - `@SpringBootTest` 加 `@ActiveProfiles("test")` 加 `@Import(AgentTestConfiguration.class)`，取得真實 bean：
      - `all()` 的名稱依序為 `待審案件, 警示卡片, 核卡初審, 刷卡風險分析, 規則問答`。
      - `核卡初審` 的工具為 3 個場景一工具。
      - `規則問答` 的工具為空。
      - `freeChatSkills()` 的名稱為 `核卡初審, 刷卡風險分析`。
      - `commonInstructions()` 包含 `信用卡經辦 AI Agent 標準作業程序`、`## 一般問答`、`【緊急警告】`。
  - `SkillControllerTest`（MockMvc）：
    - 回傳 5 筆。
    - `$[0].kind` 為 `LIST`，`$[0].source` 為 `/api/clerk/applications`，`$[0].argument` 不存在或為 null。
    - `$[2].argument` 為 `customerId`。
    - `$[4].argument` 為 `text`。
    - `$[2].instructions` 不存在。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl agent-server test -Dtest='SkillFileParserTest,SkillCatalogTest,SkillControllerTest'`
  Expected: FAIL

- [ ] **Step 3：實作** 上列型別、設定與 4 份 md 檔。

- [ ] **Step 4：執行 agent-server 全部測試**
  Run: `./mvnw -pl agent-server test`
  Expected: 全部 PASS（既有測試仍使用 `credit_card_sop.md`，不受影響）。

- [ ] **Step 5：Commit** `feat: split SOP into named skills and expose skill catalog API`

---

### Task 5：斜線指令解析與路由（純邏輯）

**Files:**
- Create：`skill/SlashCommand.java`、`skill/RoutingDecision.java`、`skill/SkillRouter.java`
- Test：`skill/SlashCommandTest.java`、`skill/SkillRouterTest.java`

**Interfaces:**
- Consumes：Task 4 的 `SkillCatalog`、`SkillDefinition`、`SkillArgument#accepts`；`ToolAllowList.ALLOWED_TOOL_NAMES`。
- Produces:
  - `record SlashCommand(String skillName, String argumentText)`：
    - `static Optional<SlashCommand> parse(String message)`：
      - 先 `strip()`。
      - 以 `/` 或 `／` 開頭才算指令。
      - 其餘部分以第一段 `\h+`（含全形空白）切成名稱與參數，參數再 `strip()`。
      - 沒有參數時為 `""`。
  - `sealed interface RoutingDecision`，有兩個 record：
    - `ReplyDirectly(String reply)`
    - `CallModel(String systemPrompt, Set<String> toolNames)`
  - `@Component SkillRouter`：`RoutingDecision route(String message)`。

**路由規則：**
1. 不是斜線指令：
   - `systemPrompt`：`commonInstructions` 加上每個 `freeChatSkills` 的內文。
   - 工具：`ALLOWED_TOOL_NAMES`。
2. 名稱查不到：`ReplyDirectly("查無此指令，請輸入 / 查看可用指令")`。
3. LIST skill：`ReplyDirectly("此指令會顯示清單，請從選單選取")`。
4. 參數不符合 `argument.accepts`：
   - `TEXT` 時回覆 `ReplyDirectly("請在指令後輸入問題")`。
   - 其他情況回覆 `ReplyDirectly("請從候選清單選取對象")`。
5. 其他情況：
   - `systemPrompt`：`commonInstructions` 加上該 skill 內文，再加上每個 `references` 對應 skill 的內文。
   - 工具：該 skill 的 `toolNames`。

各段內文以 `\n\n` 串接。

- [ ] **Step 1：寫失敗測試**
  - `SlashCommandTest`：
    - `/核卡初審 A100000003` 解析為 `("核卡初審", "A100000003")`。
    - `／核卡初審　A100000003`（全形斜線加全形空白）解析結果同上。
    - `  /規則問答 御璽卡門檻 是多少？ ` 解析為 `("規則問答", "御璽卡門檻 是多少？")`。
    - `/核卡初審` 解析為 `("核卡初審", "")`。
    - `/` 解析為 `("", "")`。
    - `請審核 A100000003` 解析為 `Optional.empty()`。
  - `SkillRouterTest`：
    - 用真實的 `SkillCatalog`（透過 `@SpringBootTest` 注入，或用 `SkillFileParser` 手動組出）。
    - `/核卡初審 A100000003`：結果為 `CallModel`，工具等於 3 個場景一工具，prompt 包含 `## 核卡初審`、不含 `## 刷卡風險分析`。
    - `/刷卡風險分析 4000000000000002`：工具等於 3 個場景二工具。
    - `/規則問答 御璽卡門檻？`：工具為空集合，prompt 包含 `## 規則問答`、`## 核卡初審`、`## 刷卡風險分析`。
    - 自由提問 `那它為什麼被凍結？`：工具等於 `ALLOWED_TOOL_NAMES`，prompt 不含 `## 規則問答`。
    - `/核卡初審 陳`、`/核卡初審 a100000003`、`/刷卡風險分析 0002`：都回覆 `ReplyDirectly("請從候選清單選取對象")`。
    - `/規則問答`：回覆 `ReplyDirectly("請在指令後輸入問題")`。
    - `/不存在 A100000003` 與 `/`：都回覆查無此指令。
    - `/待審案件`：回覆清單提示文字。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl agent-server test -Dtest='SlashCommandTest,SkillRouterTest'`
  Expected: FAIL

- [ ] **Step 3：實作** 上列三個型別。

- [ ] **Step 4：執行並確認通過**
  Run: `./mvnw -pl agent-server test -Dtest='SlashCommandTest,SkillRouterTest'`
  Expected: PASS

- [ ] **Step 5：Commit** `feat: route slash commands to skill-scoped prompts and tools`

---

### Task 6：接上 Agent 服務，移除舊 SOP

**Files:**
- Modify：`ToolAllowList.java`、`CreditCardAgentService.java`、`CreditCardAgentConfiguration.java`
- Modify（測試）：`RecordingChatModel.java`、`CreditCardAgentServiceTest.java`、`ToolAllowListTest.java`
- Delete：`agent-server/src/main/resources/skills/credit_card_sop.md`

**Interfaces:**
- Consumes：Task 5 的 `SkillRouter#route`、`RoutingDecision`。
- Produces:
  - `ToolAllowList#filter(List<ToolCallbackProvider> providers, Set<String> requestedToolNames) -> List<ToolCallback>`：結果是「allow list」與「requested」的交集。舊的單參數版本保留，委派給新版本並傳入 `ALLOWED_TOOL_NAMES`。
  - `RecordingChatModel#callCount() -> int`

- [ ] **Step 1：寫失敗測試**
  - `ToolAllowListTest`：
    - 要求 `Set.of("freezeCard")` 時，只回傳 `freezeCard`。
    - 要求 `Set.of("approveApprovalDraft")` 時，回傳空集合。
  - `CreditCardAgentServiceTest` 新增：
    - `shouldPassOnlyScenarioOneToolsWhenCardApplicationReviewInvoked`：`/核卡初審 A100000003` 只交出 3 個工具。
    - `shouldPassNoToolsWhenRuleInquiryInvoked`：`/規則問答 御璽卡門檻？` 交出 0 個工具。
    - `shouldReplyDirectlyWithoutCallingModelWhenTargetUnresolved`：
      - 記下 `callCount` 後送出 `/核卡初審 陳`。
      - 回覆等於 `請從候選清單選取對象`，`callCount` 不變。
    - `shouldKeepFullToolsForFollowUpAfterSlashCommand`：
      - 同一個 conversationId，先送 `/刷卡風險分析 4000000000000002`，再送 `那它為什麼被凍結？`。
      - 第二次交出 6 個工具，而且 prompt 包含 `4000000000000002`。
  - 既有的 6 個測試維持不改。

- [ ] **Step 2：執行並確認失敗**
  Run: `./mvnw -pl agent-server test -Dtest='CreditCardAgentServiceTest,ToolAllowListTest'`
  Expected：新測試 FAIL。

- [ ] **Step 3：實作**
  - `CreditCardAgentConfiguration` 移除 `defaultSystem` 與 SOP `Resource` 參數，只保留 memory advisor。
  - `CreditCardAgentService#chat`：
    - 呼叫 `skillRouter.route(message)`。
    - 結果是 `ReplyDirectly` 時直接回傳。
    - 結果是 `CallModel` 時，用 `.system(systemPrompt)` 與 `.toolCallbacks(toolAllowList.filter(providers, toolNames))` 呼叫模型。
  - 刪除 `credit_card_sop.md`。

- [ ] **Step 4：執行 agent-server 全部測試**
  Run: `./mvnw -pl agent-server test`
  Expected：全部 PASS（包含既有的 `shouldLoadSopIntoSystemPrompt`、`shouldAllowGeneralQuestionsInSop`）。

- [ ] **Step 5：Commit** `feat: scope agent prompt and tools by slash-command skill`

---

### Task 7：前端斜線選單、自動完成與清單

**Files:**
- Modify：`agent-server/src/main/resources/static/index.html`

**Interfaces:**
- Consumes：
  - `GET /api/agent/skills`（Task 4）。
  - `http://localhost:8081/api/clerk/applications`、`/alert-cards`、`/applicants/search?q=`、`/cards/search?q=`（Task 2、Task 3）。
  - 常數 `MCP_SERVER_URL = 'http://localhost:8081'`，寫法比照 `supervisor.html`。

**畫面調整：**
- 移除 `applicantSelect`、`cardSelect` 兩張卡片，以及對應的按鈕與事件。
- 左側改成：
  - 「待審案件」「警示卡片」兩顆按鈕，按下去等同選取對應的 LIST skill。
  - 「對話」區：`textarea`，placeholder 為 `輸入 / 使用指令，或直接提問`；下方是送出按鈕。
- `EMPTY_STATE_TEXT` 改為：`點「待審案件」或「警示卡片」挑選案件，或輸入 / 使用指令。同一段對話中可以直接追問。`

**輸入框行為：**
- 頁面載入時 fetch skills 一次。
- 內容符合 `^[/／][^\s　]*$` 時，顯示 skill 選單（依名稱前綴篩選），支援 ↑ ↓、Enter 或點選、Esc。
  - 選單開啟時按 Enter 不送出訊息。
  - 選 LIST skill：載入清單。
  - 選 AGENT skill：輸入框變成 `/名稱 `。
- 內容為 `/{AGENT 名稱} {文字}`，而且 argument 是 `customerId` 或 `cardNumber` 時：
  - 停頓 250ms 後，呼叫對應的搜尋 API，`q` 使用 `encodeURIComponent`。
  - 候選清單每列顯示：
    - 申請人：`姓名　編號　狀態`。
    - 卡片：`持卡人　末四碼 xxxx　verdictLabel　statusLabel`。
  - 選取後立即 `sendToAgent('/名稱 ' + 編號)`，並清空輸入框。

**清單渲染：**
- 用 `appendEntry` 的同款外框，標題分別為 `待審案件（N 筆）`、`警示卡片（N 筆）`。
- 狀態徽章顏色依 STYLE.md 第九節：
  - `APPROVED`：green
  - `PENDING_REVIEW`：amber
  - `FROZEN`、`SUPERVISOR_REJECTED`：red
  - `ESCALATED`：red
  - 其他：slate
- `actionable` 的列做成 `<button>`，點下去送出 `/核卡初審 {customerId}` 或 `/刷卡風險分析 {cardNumber}`；不可點的列文字呈灰色。
- 清單 fetch 失敗時，顯示 `無法取得清單：` 加上錯誤訊息，樣式同現有錯誤框。
- 所有使用者可見的文字一律用 `textContent`，不用 `innerHTML`。

- [ ] **Step 1：實作** 上述行為。
- [ ] **Step 2：確認後端測試仍綠**
  Run: `./mvnw -pl agent-server test`
  Expected: PASS
- [ ] **Step 3：手動驗證（不需要 Vertex AI）**
  - 啟動 PostgreSQL 與 mcp-server，再啟動 agent-server。
  - 開啟 `http://localhost:8080/` 檢查：
    - 輸入 `/` 出現 5 個 skill。
    - 點「待審案件」出現 7 位「待初審」。
    - 點「警示卡片」出現 5 張。
    - 輸入 `/刷卡風險分析 林` 只出現林小旅 0001。
    - 輸入全形 `／`，選單同樣出現。
- [ ] **Step 4：Commit** `feat: add slash menu, autocomplete and clickable case lists to clerk page`

---

### Task 8：文件與全量驗證

**Files:**
- Modify：`CLAUDE.md`、`STYLE.md`

- [ ] **Step 1：更新 CLAUDE.md**
  - 第二節：架構圖中的 `skills/credit_card_sop.md` 改為 `skills/*.md`；目錄結構列出 4 份 skill 檔與 `skill/` 套件。
  - 第五節：新增「經辦清單與搜尋 REST API（mcp-server，非 Tool，僅 GET）」表格，列出 4 支 API；新增 agent-server 的 `GET /api/agent/skills`。
  - 第八節：新增以下規範：
    - 斜線指令清單。
    - 每個 skill 只拿得到 frontmatter 中 `tools` 列出的工具。
    - 參數格式不符時不呼叫 LLM。
    - 自由提問維持 6 個工具。
- [ ] **Step 2：更新 STYLE.md 第五節**
  - 把「SOP 以 `Resource` 從 `classpath:skills/credit_card_sop.md` 載入，設為 `ChatClient` 的 default system prompt」改為：
    - 「SOP 拆成 `classpath:skills/*.md`，由 `SkillCatalog` 載入，`SkillRouter` 每次請求組出 system prompt。」
  - 保留「禁止把 SOP 內容寫死在 Java 字串中」這一句。
- [ ] **Step 3：全量測試**
  Run: `./mvnw test`
  Expected：BUILD SUCCESS，mcp-server 與 agent-server 全部 PASS。
- [ ] **Step 4：（可選，需要 Vertex AI）** 照設計文件第 9 節的 demo 驗收腳本走一遍。
- [ ] **Step 5：Commit** `docs: document clerk slash-command skills`
