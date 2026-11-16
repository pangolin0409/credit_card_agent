# Credit Card AI Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立信用卡經辦 AI Agent demo：mcp-server 提供受護欄保護的 MCP 工具與主管放行 API，agent-server 以 Vertex AI Gemini 依 SOP 調度工具，並提供經辦與主管的 HTML 畫面。

**Architecture:** Maven 多模組。`mcp-server`（:8081）是唯一連 PostgreSQL 的服務：Policy 類別實作規則，Tools 對 AI 暴露 6 個 `@Tool`，`/api/supervisor/**` 是不對 AI 暴露的放行 API。`agent-server`（:8080）透過 Streamable HTTP MCP Client 取得工具，再用白名單過濾，並以 `credit_card_sop.md` 作為 system prompt。

**Tech Stack:** Java 25、Spring Boot 4.1.2、Spring AI 2.0.2（`spring-ai-starter-mcp-server-webmvc`、`spring-ai-starter-mcp-client`、`spring-ai-starter-model-google-genai`）、PostgreSQL 17、JdbcClient、JUnit 5、AssertJ、Testcontainers 2、Tailwind CSS CDN。

**Spec:** [spec.md](../../../spec.md)、[CLAUDE.md](../../../CLAUDE.md)（業務規則、Tool 清單、demo 資料以此為準）、[STYLE.md](../../../STYLE.md)

## Global Constraints

- Java 25、Spring Boot 4.1.2（`spring-boot-starter-parent`）、Spring AI BOM 2.0.2、Maven Wrapper（Maven 3.9.11）。
- 設定只用 `application.properties`；密碼、專案 ID 用 `${ENV_VAR}`。
- 資料存取只用 `JdbcClient` + text block SQL，具名參數；禁止 JPA。
- DDL 只寫在 `mcp-server/src/main/resources/schema.sql`，種子 DML 只寫在 `data.sql`。
- 不用 Lombok；DTO 與結果一律用 `record`；狀態與代碼一律用 `enum`；不寫註解（`@Tool` / `@ToolParam` 的 description 除外，用英文寫）。
- 套件依功能切分：`com.demo.creditcard.mcp.{cardapplication, fraudcontrol, card, audit, supervisor, shared}`、`com.demo.creditcard.agent`。
- 規則說明與 `@DisplayName` 用中文；類別、方法、變數與指令用英文。
- 金額為 `int` 新台幣元；時間用 `LocalDateTime`。
- 稽核 operator 預設值：`AI_AGENT_MCP`。
- 每個 Task 結束時 commit，commit message 最後加一行 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- 整合測試需要 Docker daemon 在執行中。

## Review Focus

1. 經辦輸入不存在的客戶編號或卡號：Tool 應回傳明確的「查無資料」錯誤，而不是讓 Agent 收到 stack trace（Task 5、Task 6）。
2. AI 對同一申請人重複呼叫 `createApprovalDraft`：已有 `PENDING_REVIEW` 草稿時，應拒絕建立第二張（Task 5）。
3. 主管連點兩次核准：第二次要回 409，而且只能發出一張卡（Task 7）。
4. 對已凍結的卡再次 `freezeCard`：應拒絕，不可重複寫入 `TEMP_FREEZE` 稽核（Task 6）。
5. 沒有任何交易的卡（例如剛核發的新卡）：`getCardRiskProfile` 應回傳 `NO_ANOMALY`、指標皆為 0，不可拋例外（Task 4、Task 6）。

---

## File Structure

```
pom.xml                                    parent：Boot parent、Spring AI BOM、modules
.gitignore
mcp-server/pom.xml
mcp-server/src/main/java/com/demo/creditcard/mcp/
  McpServerApplication.java
  McpToolConfiguration.java                ToolCallbackProvider bean（6 個 @Tool）
  shared/ToolOutcome.java                  寫入型 Tool 的結果
  audit/AuditActionType.java
  audit/AuditLogRepository.java
  card/CardStatus.java
  card/CreditCardRepository.java           卡片查詢、凍結、新增
  card/CardRiskSubject.java                卡片 + 持卡人旅遊登錄
  card/CardNotFoundException.java
  cardapplication/CardTier.java
  cardapplication/ApplicantProfile.java
  cardapplication/ApplicationDecision.java
  cardapplication/ApplicationAssessment.java
  cardapplication/CardApplicationPolicy.java
  cardapplication/ApplicantRepository.java
  cardapplication/CustomerNotFoundException.java
  cardapplication/ApprovalDraftRequest.java
  cardapplication/ApplicantProfileResult.java
  cardapplication/CardApplicationService.java
  cardapplication/CardApplicationTools.java
  cardapplication/DraftStatus.java
  cardapplication/ApprovalDraft.java
  cardapplication/ApprovalDraftRepository.java
  fraudcontrol/TransactionRecord.java
  fraudcontrol/TransactionRepository.java
  fraudcontrol/RiskMetrics.java
  fraudcontrol/FraudVerdict.java
  fraudcontrol/FraudAssessment.java
  fraudcontrol/FraudDetectionPolicy.java
  fraudcontrol/CardRiskProfile.java
  fraudcontrol/FraudControlService.java
  fraudcontrol/FraudControlTools.java
  supervisor/ReviewRequest.java
  supervisor/ReviewResult.java
  supervisor/DraftNotFoundException.java
  supervisor/DraftAlreadyReviewedException.java
  supervisor/SupervisorApprovalService.java
  supervisor/SupervisorApprovalController.java
  supervisor/SupervisorExceptionHandler.java
  supervisor/SupervisorCorsConfiguration.java
mcp-server/src/main/resources/{application.properties, schema.sql, data.sql}
mcp-server/src/test/java/com/demo/creditcard/mcp/
  TestcontainersConfiguration.java
  DatabaseSchemaIntegrationTest.java
  McpToolRegistrationTest.java
  cardapplication/CardApplicationPolicyTest.java
  cardapplication/CardApplicationToolsIntegrationTest.java
  fraudcontrol/FraudDetectionPolicyTest.java
  fraudcontrol/FraudControlToolsIntegrationTest.java
  supervisor/SupervisorApprovalControllerIntegrationTest.java
agent-server/pom.xml
agent-server/src/main/java/com/demo/creditcard/agent/
  AgentServerApplication.java
  ToolAllowList.java
  CreditCardAgentConfiguration.java        ChatClient bean（載入 SOP）
  CreditCardAgentService.java
  ChatRequest.java / ChatReply.java
  AgentController.java
agent-server/src/main/resources/
  application.properties
  skills/credit_card_sop.md
  static/index.html
  static/supervisor.html
agent-server/src/test/java/com/demo/creditcard/agent/
  AgentTestConfiguration.java              RecordingChatModel + 假的 MCP ToolCallbackProvider
  ToolAllowListTest.java
  CreditCardAgentServiceTest.java
  AgentControllerTest.java
```

---

### Task 1: 專案骨架與 git

**Files:**
- Create: `.gitignore`、`pom.xml`、`mcp-server/pom.xml`、`agent-server/pom.xml`
- Create: `mcp-server/src/main/java/com/demo/creditcard/mcp/McpServerApplication.java`、`agent-server/src/main/java/com/demo/creditcard/agent/AgentServerApplication.java`
- Create: `mcp-server/src/main/resources/application.properties`、`agent-server/src/main/resources/application.properties`

**Interfaces:**
- Produces：兩個可編譯的 Spring Boot 模組，以及之後 Task 會用到的依賴。

- [ ] **Step 1: 初始化 git**

```bash
git init && git symbolic-ref HEAD refs/heads/main
```

`.gitignore`：`target/`、`.idea/`、`*.iml`、`.vscode/`、`.DS_Store`。

- [ ] **Step 2: 寫 parent `pom.xml`**

parent 為 `org.springframework.boot:spring-boot-starter-parent:4.1.2`；groupId `com.demo.creditcard`、artifactId `credit-card-ai-agent`、packaging `pom`；modules 為 `mcp-server`、`agent-server`；properties 設 `java.version=25`、`spring-ai.version=2.0.2`；`dependencyManagement` import `org.springframework.ai:spring-ai-bom:${spring-ai.version}`。

- [ ] **Step 3: 寫兩個模組的 pom**

`mcp-server` 的依賴：`spring-boot-starter-jdbc`、`spring-boot-starter-webmvc`、`spring-boot-starter-validation`、`spring-ai-starter-mcp-server-webmvc`、`org.postgresql:postgresql`（runtime）。
測試依賴：`spring-boot-starter-test`、`spring-boot-starter-webmvc-test`、`spring-boot-testcontainers`、`org.testcontainers:testcontainers-postgresql`、`org.testcontainers:testcontainers-junit-jupiter`。

`agent-server` 的依賴：`spring-boot-starter-webmvc`、`spring-boot-starter-validation`、`spring-ai-starter-model-google-genai`、`spring-ai-starter-mcp-client`。
測試依賴：`spring-boot-starter-test`、`spring-boot-starter-webmvc-test`。

兩個模組都要加 `spring-boot-maven-plugin`。

- [ ] **Step 4: 寫 Application 類別與 properties**

`mcp-server/application.properties`：

```properties
server.port=8081
spring.application.name=mcp-server

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.sql.init.mode=always

spring.ai.mcp.server.name=credit-card-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.protocol=STREAMABLE
```

`agent-server/application.properties`：

```properties
server.port=8080
spring.application.name=agent-server

spring.ai.google.genai.vertex-ai=true
spring.ai.google.genai.project-id=${GOOGLE_CLOUD_PROJECT}
spring.ai.google.genai.location=${GOOGLE_CLOUD_LOCATION}
spring.ai.google.genai.chat.options.model=${VERTEX_AI_MODEL}

spring.ai.mcp.client.type=SYNC
spring.ai.mcp.client.streamable-http.connections.credit-card-mcp.url=${MCP_SERVER_URL:http://localhost:8081}
```

- [ ] **Step 5: 產生 Maven Wrapper 並驗證編譯**

Run: `mvn -N wrapper:wrapper -Dmaven=3.9.11 && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS，兩個模組都產出 jar。

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "chore: scaffold maven multi-module project" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 資料庫 schema 與 demo 資料

**Files:**
- Create: `mcp-server/src/main/resources/schema.sql`、`mcp-server/src/main/resources/data.sql`
- Test: `mcp-server/src/test/java/com/demo/creditcard/mcp/TestcontainersConfiguration.java`、`DatabaseSchemaIntegrationTest.java`

**Interfaces:**
- Produces：`TestcontainersConfiguration`（`@TestConfiguration(proxyBeanMethods = false)`，內含 `@Bean @ServiceConnection PostgreSQLContainer postgresContainer()`，image `postgres:17-alpine`）。之後所有 mcp-server 整合測試都用 `@SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional`。
- Produces：CLAUDE.md 第七節的 demo 資料（具體數值見下方 Step 3）。

- [ ] **Step 1: 寫失敗的測試**

```java
@Test
@DisplayName("啟動時載入 demo 資料：13 位客戶、6 張卡、15 筆交易")
void shouldSeedDemoData() {
    assertThat(count("customers")).isEqualTo(13);
    assertThat(count("credit_cards")).isEqualTo(6);
    assertThat(count("transactions")).isEqualTo(15);
    assertThat(count("approval_drafts")).isZero();
    assertThat(count("audit_logs")).isZero();
}

@Test
@DisplayName("卡片狀態只允許 ACTIVE、TEMP_FROZEN、CLOSED")
void shouldRejectUnknownCardStatus() {
    assertThatThrownBy(() -> jdbcClient.sql("UPDATE credit_cards SET status = 'LOST' WHERE card_number = '4000000000000006'").update())
            .isInstanceOf(DataIntegrityViolationException.class);
}

@Test
@DisplayName("稽核動作類型只允許 CLAUDE.md 定義的代碼")
void shouldRejectUnknownAuditActionType() { /* INSERT action_type 'DELETE_CARD' → DataIntegrityViolationException */ }

@Test
@DisplayName("草稿單預設狀態為 PENDING_REVIEW、建立者為 AI_AGENT_MCP")
void shouldDefaultDraftStatusToPendingReview() { /* INSERT 只帶必填欄位，再 SELECT status, created_by 驗證 */ }
```

`count(table)` 是測試內的私有方法，使用 `SELECT COUNT(*)`。

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest=DatabaseSchemaIntegrationTest`
Expected: FAIL（找不到 table `customers`）。

- [ ] **Step 3: 寫 `schema.sql` 與 `data.sql`**

`schema.sql`：
- 開頭依序 `DROP TABLE IF EXISTS audit_logs, approval_drafts, transactions, credit_cards, customers CASCADE;`。
- 5 張表的欄位依 spec.md 第三節與 CLAUDE.md 第六節。
- 約束命名依 STYLE.md 第六節。
- 加上 `CHECK`：`ck_credit_cards_status`、`ck_credit_cards_card_type`、`ck_approval_drafts_status`、`ck_approval_drafts_card_type`、`ck_audit_logs_action_type`。
- 加上索引 `idx_transactions_card_time (card_number, transaction_time)`。

`data.sql` 的申請人（`has_travel_notice=false`、`travel_country=NULL`）：

| customer_id | full_name | monthly_income | existing_unsecured_debt | credit_score | is_aml_blacklist |
|---|---|---|---|---|---|
| A100000001 | 王志明 | 300000 | 0 | 800 | TRUE |
| A100000002 | 林淑芬 | 60000 | 1250000 | 720 | FALSE |
| A100000003 | 陳建宏 | 200000 | 0 | 780 | FALSE |
| A100000004 | 張雅婷 | 80000 | 0 | 700 | FALSE |
| A100000005 | 李俊傑 | 60000 | 1200000 | 680 | FALSE |
| A100000006 | 黃美惠 | 200000 | 0 | 700 | FALSE |
| A100000007 | 吳宗翰 | 40000 | 0 | 700 | FALSE |

持卡人（月收 80000、負債 0、分數 720、非黑名單）與卡片（status 皆為 `ACTIVE`）：

| customer_id | full_name | travel | card_number | card_type | credit_limit |
|---|---|---|---|---|---|
| B200000001 | 林小旅 | JP | 4000000000000001 | SIGNATURE | 150000 |
| B200000002 | 陳大明 | — | 4000000000000002 | INFINITE | 500000 |
| B200000003 | 張美玲 | JP | 4000000000000003 | SIGNATURE | 150000 |
| B200000004 | 黃志強 | — | 4000000000000004 | SIGNATURE | 150000 |
| B200000005 | 吳建國 | — | 4000000000000005 | INFINITE | 1000000 |
| B200000006 | 李小華 | — | 4000000000000006 | SIGNATURE | 150000 |

交易（日期皆為 2026-11-16，另有註記者除外；transaction_id 格式 `TX` + 卡號末碼 + 兩位序號，例如 `TX101`）：

| 卡號末碼 | 時間 | amount | merchant_name | country | online |
|---|---|---|---|---|---|
| 1 | 09:00 | 85000 | Bic Camera Shinjuku | JP | false |
| 1 | 09:40 | 42000 | Isetan Shinjuku | JP | false |
| 2 | 2026-11-15 20:00 | 1200 | 全聯福利中心 | TW | false |
| 2 | 02:05 | 18000 | BestBuy Online | US | true |
| 2 | 02:25 | 21000 | Apple Store Online | US | true |
| 2 | 02:50 | 23000 | Amazon US | US | true |
| 3 | 03:10 | 30000 | Galeries Lafayette Online | FR | true |
| 3 | 03:40 | 25000 | Fnac Online | FR | true |
| 4 | 04:00 | 1 | GOOGLE *TEMPORARY HOLD | US | true |
| 4 | 04:03 | 5 | STEAM PURCHASE | US | true |
| 4 | 04:06 | 30 | APPLE.COM/BILL | US | true |
| 4 | 04:08 | 50 | PAYPAL *VERIFY | US | true |
| 5 | 05:00 | 680000 | Rolex Geneva Online | CH | true |
| 6 | 12:00 | 350 | 7-ELEVEN | TW | false |
| 6 | 12:30 | 1280 | 誠品書店 | TW | false |

- [ ] **Step 4: 執行測試，確認通過**

Run: `./mvnw -pl mcp-server test -Dtest=DatabaseSchemaIntegrationTest`
Expected: PASS（4 tests）。

- [ ] **Step 5: Commit**（`feat: add database schema and demo seed data`）

---

### Task 3: 核卡規則 `CardApplicationPolicy`

**Files:**
- Create: `cardapplication/{CardTier, ApplicantProfile, ApplicationDecision, ApplicationAssessment, CardApplicationPolicy}.java`
- Test: `cardapplication/CardApplicationPolicyTest.java`（純單元測試，直接 `new CardApplicationPolicy()`）

**Interfaces:**
- Produces:
  - `enum CardTier { INFINITE(500_000), SIGNATURE(150_000); int maximumCreditLimit() }`
  - `record ApplicantProfile(String customerId, String fullName, int monthlyIncome, int existingUnsecuredDebt, int creditScore, boolean isAmlBlacklisted)`
  - `enum ApplicationDecision { DECLINE_AML_BLACKLIST, DECLINE_DEBT_BURDEN, DECLINE_BELOW_THRESHOLD, RECOMMEND_APPROVAL }`
  - `record ApplicationAssessment(ApplicationDecision decision, CardTier recommendedCardTier, int recommendedCreditLimit, int remainingDebtCapacity, String rationale)`：拒絕時 `recommendedCardTier = null`、`recommendedCreditLimit = 0`
  - `@Component CardApplicationPolicy`：
    - `ApplicationAssessment assess(ApplicantProfile applicant)`
    - `Optional<String> findDraftViolation(ApplicationAssessment assessment, CardTier requestedCardTier, int requestedCreditLimit)`

- [ ] **Step 1: 寫失敗的測試**

測試資料用 `applicant(income, debt, score, blacklisted)` helper 建立。

| 測試方法 | 輸入（income, debt, score, blacklist） | 斷言 |
|---|---|---|
| `shouldDeclineBlacklistedApplicantRegardlessOfIncome` | 300000, 0, 800, true | `DECLINE_AML_BLACKLIST` |
| `shouldDeclineWhenRemainingDebtCapacityBelowMinimum` | 60000, 1250000, 720, false | `DECLINE_DEBT_BURDEN`、`remainingDebtCapacity == 70000` |
| `shouldDeclineWhenRemainingDebtCapacityIsOneBelowMinimum` | 50000, 1000001, 700, false | `DECLINE_DEBT_BURDEN` |
| `shouldRecommendWhenRemainingDebtCapacityExactlyMinimum` | 50000, 1000000, 700, false | `SIGNATURE`、limit 100000 |
| `shouldRecommendInfiniteAtExactThreshold` | 150000, 0, 750, false | `INFINITE`、limit 500000 |
| `shouldRecommendSignatureWhenScoreBelowInfiniteThreshold` | 200000, 0, 749, false | `SIGNATURE`、limit 150000 |
| `shouldRecommendSignatureWhenIncomeBelowInfiniteThreshold` | 149999, 0, 800, false | `SIGNATURE` |
| `shouldRecommendSignatureAtExactThreshold` | 50000, 0, 650, false | `SIGNATURE`、limit 150000 |
| `shouldCapCreditLimitByRemainingDebtCapacity` | 60000, 1200000, 680, false | `SIGNATURE`、limit 120000 |
| `shouldDeclineWhenIncomeBelowSignatureThreshold` | 49999, 0, 700, false | `DECLINE_BELOW_THRESHOLD` |
| `shouldDeclineWhenScoreBelowSignatureThreshold` | 80000, 0, 649, false | `DECLINE_BELOW_THRESHOLD` |

`findDraftViolation` 的測試（assessment 用 `assess(...)` 產生）：
- 黑名單的 assessment → `isPresent()`
- 建議 SIGNATURE/120000，要求 SIGNATURE/150000 → present
- 建議 SIGNATURE/150000，要求 INFINITE/500000 → present
- 建議 SIGNATURE/150000，要求 SIGNATURE/0 → present
- 建議 SIGNATURE/150000，要求 SIGNATURE/150000 → `isEmpty()`

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest=CardApplicationPolicyTest`
Expected: 編譯失敗（找不到 `CardApplicationPolicy`）。

- [ ] **Step 3: 實作**

常數依 STYLE.md 第一節命名，順序依 CLAUDE.md 第四節「命中即停」。`rationale` 用中文寫出命中的規則與計算數字。

- [ ] **Step 4: 執行測試，確認通過**

Run: 同 Step 2。Expected: PASS（16 tests）。

- [ ] **Step 5: Commit**（`feat: add card application policy`）

---

### Task 4: 封控規則 `FraudDetectionPolicy`

**Files:**
- Create: `card/CardStatus.java`、`card/CardRiskSubject.java`、`fraudcontrol/{TransactionRecord, RiskMetrics, FraudVerdict, FraudAssessment, FraudDetectionPolicy}.java`
- Test: `fraudcontrol/FraudDetectionPolicyTest.java`（純單元測試）

**Interfaces:**
- Produces:
  - `enum CardStatus { ACTIVE, TEMP_FROZEN, CLOSED }`
  - `record CardRiskSubject(String cardNumber, String customerId, String fullName, CardStatus status, boolean hasTravelNotice, String travelCountry)`
  - `record TransactionRecord(String transactionId, LocalDateTime transactionTime, int amount, String merchantName, String countryCode, boolean isOnline)`
  - `record RiskMetrics(int maxSingleAmount, int foreignOnlineAmountWithinOneHour, int microTransactionCountWithinTenMinutes)`
  - `enum FraudVerdict { ESCALATE_TO_RISK_OFFICER, NORMAL_TRAVEL, OVERSEAS_FRAUD, CARD_TESTING, NO_ANOMALY }`
  - `record FraudAssessment(FraudVerdict verdict, RiskMetrics metrics, String rationale)`
  - `@Component FraudDetectionPolicy`：
    - `FraudAssessment assess(CardRiskSubject subject, List<TransactionRecord> transactions)`
    - `boolean exceedsEscalationThreshold(List<TransactionRecord> transactions)`

規則定義（時間基準 `latest` = 傳入交易中最晚的 `transactionTime`；國內代碼為 `TW`）：
- 紅線：任一 `amount > 500_000`。
- 旅遊：`hasTravelNotice`，且至少有一筆國外交易，且所有國外交易的 `countryCode` 都等於 `travelCountry`。
- `foreignOnlineAmountWithinOneHour`：符合 `transactionTime >= latest - 60 分鐘`、`countryCode != TW`、`isOnline`、且 `countryCode != travelCountry`（未登錄旅遊時不排除任何國家）的交易金額加總；`> 50_000` 即判定 `OVERSEAS_FRAUD`。
- `microTransactionCountWithinTenMinutes`：`transactionTime >= latest - 10 分鐘` 且 `amount <= 50` 的筆數；`>= 3` 即判定 `CARD_TESTING`。
- 交易清單為空：三項指標皆為 0，判定 `NO_ANOMALY`。

- [ ] **Step 1: 寫失敗的測試**

測試資料用 `transaction(time, amount, country, online)` 與 `subject(travelCountry)` helper 建立；`subject(null)` 代表未登錄旅遊。

| 測試方法 | 情境 | 斷言 |
|---|---|---|
| `shouldEscalateWhenSingleAmountExceedsThreshold` | 單筆 500001 CH 網路，未登錄 | `ESCALATE_TO_RISK_OFFICER`、`maxSingleAmount == 500001` |
| `shouldNotEscalateAtExactThreshold` | 單筆 500000 TW 實體 | 不是 `ESCALATE_TO_RISK_OFFICER` |
| `shouldEscalateBeforeOverseasFraud` | 680000 CH 網路，未登錄 | `ESCALATE_TO_RISK_OFFICER` |
| `shouldTreatMatchingTravelCountryAsNormal` | 登錄 JP，JP 85000 + 42000 | `NORMAL_TRAVEL` |
| `shouldIgnoreTravelNoticeWhenNoForeignTransactions` | 登錄 JP，只有 TW 交易 | `NO_ANOMALY` |
| `shouldFlagOverseasFraudWhenNoTravelNotice` | 未登錄，US 網路 18000/21000/23000，45 分鐘內 | `OVERSEAS_FRAUD`、foreign == 62000 |
| `shouldFlagOverseasFraudWhenTravelCountryMismatches` | 登錄 JP，FR 網路 30000 + 25000 | `OVERSEAS_FRAUD`、foreign == 55000 |
| `shouldNotFlagOverseasFraudAtExactThreshold` | 未登錄，US 網路共 50000 | `NO_ANOMALY` |
| `shouldExcludeTransactionsOlderThanOneHour` | 未登錄，US 網路 30000 在 latest−61 分、30000 在 latest | foreign == 30000、`NO_ANOMALY` |
| `shouldIncludeTransactionExactlyOneHourBefore` | 30000 在 latest−60 分、30000 在 latest | foreign == 60000、`OVERSEAS_FRAUD` |
| `shouldFlagCardTestingWithThreeMicroTransactions` | 1/5/50 元在 10 分鐘內 | `CARD_TESTING`、micro == 3 |
| `shouldNotFlagCardTestingWithTwoMicroTransactions` | 5/50 元，另有 51 元 | `NO_ANOMALY`、micro == 2 |
| `shouldExcludeMicroTransactionsOlderThanTenMinutes` | 1 元在 latest−11 分，5/30 元在 10 分鐘內 | micro == 2 |
| `shouldReturnNoAnomalyForEmptyTransactions` | 空清單 | `NO_ANOMALY`、`new RiskMetrics(0, 0, 0)` |
| `exceedsEscalationThreshold` 兩個案例 | 500001 / 500000 | true / false |

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest=FraudDetectionPolicyTest`
Expected: 編譯失敗。

- [ ] **Step 3: 實作**

判斷順序：紅線 → 旅遊 → 境外盜刷 → 測卡 → 無異常。三項指標不論命中哪條規則都要算出來。

- [ ] **Step 4: 執行測試，確認通過**

Run: 同 Step 2。Expected: PASS（16 tests）。

- [ ] **Step 5: Commit**（`feat: add fraud detection policy`）

---

### Task 5: 場景一 MCP 工具

**Files:**
- Create: `shared/ToolOutcome.java`、`audit/{AuditActionType, AuditLogRepository}.java`、`cardapplication/{ApplicantRepository, CustomerNotFoundException, DraftStatus, ApprovalDraft, ApprovalDraftRepository, ApprovalDraftRequest, ApplicantProfileResult, CardApplicationService, CardApplicationTools}.java`
- Test: `cardapplication/CardApplicationToolsIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 `CardApplicationPolicy`、Task 2 `TestcontainersConfiguration`。
- Produces:
  - `record ToolOutcome(boolean accepted, String message)`，附 `static accepted(String)`、`static rejected(String)`。
  - `enum AuditActionType { APPROVAL_DRAFT, APPLICATION_DECLINE, TEMP_FREEZE, ESCALATION, GUARDRAIL_BLOCKED, DRAFT_APPROVED, DRAFT_REJECTED }`
  - `AuditLogRepository`：`static final String AI_OPERATOR = "AI_AGENT_MCP"`；`void append(String targetId, AuditActionType actionType, String reason, String operator)`。
  - `ApplicantRepository.findById(String customerId): Optional<ApplicantProfile>`
  - `enum DraftStatus { PENDING_REVIEW, APPROVED, REJECTED }`
  - `record ApprovalDraft(int draftId, String customerId, String fullName, CardTier cardType, int creditLimit, String assessmentSummary, DraftStatus status, LocalDateTime createdAt, String reviewedBy, LocalDateTime reviewedAt)`
  - `ApprovalDraftRepository`：
    - `int insert(ApprovalDraftRequest request)`，回傳 `draftId`
    - `boolean existsPendingByCustomerId(String customerId)`
    - `List<ApprovalDraft> findByStatus(DraftStatus status)`（JOIN customers 帶出 `full_name`）
    - `Optional<ApprovalDraft> findById(int draftId)`
    - `void markReviewed(int draftId, DraftStatus status, String reviewedBy)`（同時設定 `reviewed_at = CURRENT_TIMESTAMP`）
  - `record ApprovalDraftRequest(String customerId, CardTier cardTier, int creditLimit, String assessmentSummary)`
  - `record ApplicantProfileResult(ApplicantProfile applicant, ApplicationAssessment assessment)`
  - `CardApplicationTools`（`@Tool` 的 name 等於方法名）：
    - `ApplicantProfileResult getApplicantProfile(String customerId)`：查無資料時拋 `CustomerNotFoundException`，訊息為 `"Customer not found: " + customerId`
    - `ToolOutcome createApprovalDraft(ApprovalDraftRequest request)`
    - `ToolOutcome declineApplication(String customerId, String reason)`

`createApprovalDraft` 的流程（`@Transactional`，寫在 `CardApplicationService`）：
1. 查申請人，查無時拋例外。
2. 已有 `PENDING_REVIEW` 草稿 → `rejected`。
3. `policy.findDraftViolation(...)` 有違規 → `rejected(違規原因)`。
4. 第 2 或 3 步拒絕時，都要寫入 `GUARDRAIL_BLOCKED`（target = customerId）。
5. 通過時 insert 草稿，寫入 `APPROVAL_DRAFT`，回傳 `accepted`，message 要包含 draftId。

- [ ] **Step 1: 寫失敗的測試**

測試類別加上 `@SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional`，並注入 `CardApplicationTools`、`JdbcClient`。

| 測試方法 | 動作 | 斷言 |
|---|---|---|
| `shouldReturnInfiniteAssessmentForQualifiedApplicant` | `getApplicantProfile("A100000003")` | `RECOMMEND_APPROVAL`、`INFINITE`、500000、`remainingDebtCapacity == 4400000` |
| `shouldReturnCappedLimitForDebtConstrainedApplicant` | `getApplicantProfile("A100000005")` | `SIGNATURE`、120000 |
| `shouldThrowWhenCustomerNotFound` | `getApplicantProfile("Z999999999")` | 拋 `CustomerNotFoundException`，訊息包含 `Z999999999` |
| `shouldCreatePendingDraftWithoutIssuingCard` | `createApprovalDraft(A100000004, SIGNATURE, 150000, "符合御璽卡")` | accepted；草稿 1 筆且為 `PENDING_REVIEW`；`credit_cards` 仍為 6 筆；`APPROVAL_DRAFT` 稽核 1 筆，operator `AI_AGENT_MCP` |
| `shouldBlockDraftForBlacklistedApplicant` | A100000001, INFINITE, 500000 | rejected；草稿 0 筆；`GUARDRAIL_BLOCKED` 1 筆 |
| `shouldBlockDraftExceedingCappedLimit` | A100000005, SIGNATURE, 150000 | rejected |
| `shouldBlockDraftWithMismatchedTier` | A100000006, INFINITE, 500000 | rejected |
| `shouldBlockDuplicatePendingDraft` | A100000004 連續建立兩次 | 第二次 rejected；草稿 1 筆 |
| `shouldRecordDecline` | `declineApplication("A100000002", "負債比過高")` | accepted；`APPLICATION_DECLINE` 1 筆，reason 為 `負債比過高` |

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest=CardApplicationToolsIntegrationTest`
Expected: 編譯失敗。

- [ ] **Step 3: 實作**

Repository、Service、Tools 依上方 Interfaces 實作。`@Tool` 的 description 用英文寫出用途與拒絕條件。

- [ ] **Step 4: 執行測試，確認通過**

Run: 同 Step 2。Expected: PASS（9 tests）。

- [ ] **Step 5: Commit**（`feat: add card application MCP tools`）

---

### Task 6: 場景二 MCP 工具與工具註冊

**Files:**
- Create: `card/{CreditCardRepository, CardNotFoundException}.java`、`fraudcontrol/{TransactionRepository, CardRiskProfile, FraudControlService, FraudControlTools}.java`、`McpToolConfiguration.java`
- Test: `fraudcontrol/FraudControlToolsIntegrationTest.java`、`McpToolRegistrationTest.java`

**Interfaces:**
- Consumes: Task 4 的 `FraudDetectionPolicy` 與各個 record；Task 5 的 `AuditLogRepository`、`ToolOutcome`、`CardApplicationTools`。
- Produces:
  - `CreditCardRepository`：
    - `Optional<CardRiskSubject> findRiskSubject(String cardNumber)`（JOIN customers）
    - `void freeze(String cardNumber, String freezeReason)`
    - `void insertActiveCard(String cardNumber, String customerId, CardTier cardType, int creditLimit)`（Task 8 使用）
  - `TransactionRepository.findRecentByCardNumber(String cardNumber): List<TransactionRecord>`：只取該卡最新交易往回 24 小時內的交易，依時間遞增排序。
  - `record CardRiskProfile(CardRiskSubject card, List<TransactionRecord> recentTransactions, FraudAssessment assessment)`
  - `FraudControlTools`：
    - `CardRiskProfile getCardRiskProfile(String cardNumber)`：查無時拋 `CardNotFoundException("Card not found: " + cardNumber)`
    - `ToolOutcome freezeCard(String cardNumber, String freezeReason)`
    - `ToolOutcome escalateToRiskOfficer(String cardNumber, String caseSummary)`
  - `McpToolConfiguration`：`@Bean ToolCallbackProvider creditCardTools(CardApplicationTools, FraudControlTools)`，用 `MethodToolCallbackProvider.builder().toolObjects(...)` 建立。

`freezeCard` 的流程（`@Transactional`）：
1. 查卡，查無時拋例外。
2. 狀態不是 `ACTIVE` → 拒絕。
3. `exceedsEscalationThreshold(近期交易)` → 拒絕，訊息要指示改用 `escalateToRiskOfficer`。
4. 第 2 或 3 步拒絕時，寫入 `GUARDRAIL_BLOCKED`（target = cardNumber）。
5. 通過時 `freeze`，寫入 `TEMP_FREEZE`。

`escalateToRiskOfficer`：查卡（查無時拋例外）→ 寫入 `ESCALATION`（reason = caseSummary）→ `accepted`。不改變卡片狀態。

- [ ] **Step 1: 寫失敗的測試**

`FraudControlToolsIntegrationTest`：

| 測試方法 | 動作 | 斷言 |
|---|---|---|
| `shouldAssessDemoCards`（`@ParameterizedTest @CsvSource`） | 卡號 0001～0006 | 分別為 `NORMAL_TRAVEL`、`OVERSEAS_FRAUD`、`OVERSEAS_FRAUD`、`CARD_TESTING`、`ESCALATE_TO_RISK_OFFICER`、`NO_ANOMALY` |
| `shouldExposeRiskMetrics` | 0002 | foreign == 62000；`recentTransactions` 共 4 筆 |
| `shouldThrowWhenCardNotFound` | `getCardRiskProfile("4000000000009999")` | 拋 `CardNotFoundException` |
| `shouldReturnNoAnomalyForCardWithoutTransactions` | 用 JdbcClient 新增卡號 `4000000000000099`（屬於 B200000006，沒有交易）後查詢 | `NO_ANOMALY`、`recentTransactions` 為空 |
| `shouldFreezeCardAndRecordAudit` | `freezeCard(0002, "境外盜刷")` | accepted；status 為 `TEMP_FROZEN`；`freeze_reason` 為 `境外盜刷`；`TEMP_FREEZE` 1 筆 |
| `shouldBlockFreezeOnEscalationCase` | `freezeCard(0005, ...)` | rejected；status 仍為 `ACTIVE`；`GUARDRAIL_BLOCKED` 1 筆 |
| `shouldBlockFreezeOnAlreadyFrozenCard` | 0004 凍結兩次 | 第二次 rejected；`TEMP_FREEZE` 只有 1 筆 |
| `shouldRecordEscalationWithoutChangingStatus` | `escalateToRiskOfficer(0005, "單筆 680,000 CH 網路交易")` | accepted；`ESCALATION` 1 筆；status 為 `ACTIVE` |

`McpToolRegistrationTest`：

```java
@Test
@DisplayName("MCP Server 只暴露 6 個工具，且不含主管放行功能")
void shouldExposeExactlySixTools() {
    assertThat(toolNames()).containsExactlyInAnyOrder(
            "getApplicantProfile", "createApprovalDraft", "declineApplication",
            "getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");
}
```

`toolNames()` 從注入的 `ToolCallbackProvider` 取得 `getToolCallbacks()` 的 `getToolDefinition().name()`。

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest='FraudControlToolsIntegrationTest,McpToolRegistrationTest'`
Expected: 編譯失敗。

- [ ] **Step 3: 實作**

`findRecentByCardNumber` 的 SQL 以子查詢 `MAX(transaction_time)` 減去 `INTERVAL '24 hours'` 作為下界。

- [ ] **Step 4: 執行 mcp-server 全部測試，確認通過**

Run: `./mvnw -pl mcp-server test`
Expected: PASS（Task 2～6 全部）。

- [ ] **Step 5: Commit**（`feat: add fraud control MCP tools and register tool provider`）

---

### Task 7: 主管放行 REST API

**Files:**
- Create: `supervisor/{ReviewRequest, ReviewResult, DraftNotFoundException, DraftAlreadyReviewedException, SupervisorApprovalService, SupervisorApprovalController, SupervisorExceptionHandler, SupervisorCorsConfiguration}.java`
- Test: `supervisor/SupervisorApprovalControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 5 的 `ApprovalDraftRepository`、`CardApplicationTools`、`AuditLogRepository`；Task 6 的 `CreditCardRepository.insertActiveCard`。
- Produces（前端 Task 9 會呼叫）：
  - `record ReviewRequest(@NotBlank String supervisorId)`
  - `record ReviewResult(int draftId, DraftStatus status, String cardNumber)`：駁回時 `cardNumber = null`
  - `GET /api/supervisor/approval-drafts?status=PENDING_REVIEW` → `List<ApprovalDraft>`
  - `POST /api/supervisor/approval-drafts/{draftId}/approve`（body：`ReviewRequest`）→ `ReviewResult`
  - `POST /api/supervisor/approval-drafts/{draftId}/reject`（body：`ReviewRequest`）→ `ReviewResult`
  - 錯誤回應：`DraftNotFoundException` → 404、`DraftAlreadyReviewedException` → 409、驗證失敗 → 400。
  - CORS：`/api/supervisor/**` 允許 origin `http://localhost:8080`，methods 為 GET 與 POST。

核准流程（`@Transactional`）：
1. 查草稿，查無時拋 404。
2. 狀態不是 `PENDING_REVIEW` 時拋 409。
3. 卡號為 `"4999%012d".formatted(draftId)`，呼叫 `insertActiveCard`。
4. `markReviewed(APPROVED, supervisorId)`。
5. 寫入 `DRAFT_APPROVED`（target = customerId，operator = supervisorId）。

駁回流程相同，但不發卡，並寫入 `DRAFT_REJECTED`。

- [ ] **Step 1: 寫失敗的測試**

測試類別加上 `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class) @Transactional`。草稿用 `cardApplicationTools.createApprovalDraft(A100000004, SIGNATURE, 150000, ...)` 建立，`draftId` 用 JdbcClient 查出。

| 測試方法 | 動作 | 斷言 |
|---|---|---|
| `shouldListPendingDrafts` | GET | 200；`$[0].customerId == "A100000004"`、`$[0].fullName == "張雅婷"` |
| `shouldIssueCardOnApproval` | POST approve `{"supervisorId":"SUPERVISOR_01"}` | 200；`status == APPROVED`；`cardNumber` 符合 `4999\d{12}`；`credit_cards` 有該卡（A100000004、SIGNATURE、150000、ACTIVE）；草稿 `reviewed_by == SUPERVISOR_01`；`DRAFT_APPROVED` 1 筆且 operator 為 `SUPERVISOR_01` |
| `shouldRejectSecondApproval` | 連續 approve 兩次 | 第二次 409；`credit_cards` 為 7 筆 |
| `shouldNotIssueCardOnRejection` | POST reject | 200；`status == REJECTED`；`credit_cards` 仍為 6 筆；`DRAFT_REJECTED` 1 筆 |
| `shouldReturnNotFoundForUnknownDraft` | approve 999999 | 404 |
| `shouldRequireSupervisorId` | approve `{"supervisorId":""}` | 400 |
| `shouldAllowCorsFromAgentServer` | OPTIONS 帶 `Origin: http://localhost:8080` 與 `Access-Control-Request-Method: POST` | header `Access-Control-Allow-Origin == http://localhost:8080` |

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl mcp-server test -Dtest=SupervisorApprovalControllerIntegrationTest`
Expected: 編譯失敗。

- [ ] **Step 3: 實作**

- [ ] **Step 4: 執行 mcp-server 全部測試，確認通過**

Run: `./mvnw -pl mcp-server test`
Expected: PASS。`McpToolRegistrationTest` 仍為 6 個工具。

- [ ] **Step 5: Commit**（`feat: add supervisor approval API`）

---

### Task 8: Agent 核心（SOP 載入與工具白名單）

**Files:**
- Create: `agent/{ToolAllowList, CreditCardAgentConfiguration, CreditCardAgentService}.java`、`agent-server/src/main/resources/skills/credit_card_sop.md`
- Test: `agent/{AgentTestConfiguration, ToolAllowListTest, CreditCardAgentServiceTest}.java`

**Interfaces:**
- Produces:
  - `@Component ToolAllowList`：
    - `static final Set<String> ALLOWED_TOOL_NAMES` 為 Task 6 的 6 個名稱
    - `List<ToolCallback> filter(List<ToolCallbackProvider> providers)`
  - `CreditCardAgentConfiguration`：`@Bean ChatClient creditCardAgentChatClient(ChatClient.Builder builder, @Value("classpath:skills/credit_card_sop.md") Resource sop)`，以 `defaultSystem(sop)` 設定 system prompt。
  - `@Service CreditCardAgentService`：`String chat(String message)`。每次呼叫時才對 `List<ToolCallbackProvider>` 做 `filter`，再以 `.toolCallbacks(...)` 傳入，這樣 MCP 工具清單是在請求當下取得的。
  - `AgentTestConfiguration`（`@TestConfiguration`）提供：
    - `RecordingChatModel`：實作 `ChatModel`，記錄最後一次收到的 `Prompt`，固定回傳 `"stub reply"`；`getDefaultOptions()` 回傳 `ToolCallingChatOptions.builder().build()`。
    - `ToolCallbackProvider fakeMcpTools()`：7 個假工具，即 6 個白名單工具加上 `approveApprovalDraft`。
  - 測試屬性：`spring.ai.mcp.client.enabled=false`、`spring.ai.model.chat=none`。

`credit_card_sop.md` 必須包含的內容：
- 標題：`# 信用卡經辦 AI Agent 標準作業程序`
- 章節：`## 場景一：新戶核卡初審`、`## 場景二：異常刷卡通報封鎖`、`## 權限與護欄`、`## 回覆格式`
- 內容涵蓋 CLAUDE.md 第四節與第八節的全部規則與數值。
- 回覆格式：使用繁體中文純文字，不使用 Markdown；紅線案件的回覆第一行固定為 `【緊急警告】`。

- [ ] **Step 1: 寫失敗的測試**

`ToolAllowListTest`（單元測試）：

```java
@Test
@DisplayName("白名單過濾後只保留 6 個工具，主管放行工具被排除")
void shouldKeepOnlyAllowListedTools() {
    var names = new ToolAllowList().filter(List.of(providerOf(SEVEN_TOOL_NAMES))).stream()
            .map(callback -> callback.getToolDefinition().name()).toList();

    assertThat(names).containsExactlyInAnyOrderElementsOf(ToolAllowList.ALLOWED_TOOL_NAMES)
            .doesNotContain("approveApprovalDraft");
}
```

`CreditCardAgentServiceTest`（`@SpringBootTest` + `@Import(AgentTestConfiguration.class)`）：

| 測試方法 | 斷言 |
|---|---|
| `shouldLoadSopIntoSystemPrompt` | `chat("請審核客戶 A100000003")` 之後，記錄到的 Prompt 的 system message 包含 `信用卡經辦 AI Agent 標準作業程序` 與 `【緊急警告】` |
| `shouldPassOnlyAllowListedToolsToModel` | 記錄到的 Prompt options（`ToolCallingChatOptions`）的 tool callback 名稱恰為 6 個白名單名稱 |
| `shouldReturnModelReply` | 回傳值為 `"stub reply"` |

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl agent-server test`
Expected: 編譯失敗。

- [ ] **Step 3: 實作並撰寫 SOP**

- [ ] **Step 4: 執行測試，確認通過**

Run: `./mvnw -pl agent-server test`
Expected: PASS（4 tests）。

- [ ] **Step 5: Commit**（`feat: add agent core with SOP skill and tool allow list`）

---

### Task 9: Agent API 與前端畫面

**Files:**
- Create: `agent/{ChatRequest, ChatReply, AgentController}.java`、`agent-server/src/main/resources/static/{index.html, supervisor.html}`
- Test: `agent/AgentControllerTest.java`

**Interfaces:**
- Consumes: Task 8 的 `CreditCardAgentService.chat`；Task 7 的主管 API。
- Produces:
  - `record ChatRequest(@NotBlank String message)`、`record ChatReply(String reply)`
  - `POST /api/agent/chat` → `ChatReply`；message 空白時回 400。

- [ ] **Step 1: 寫失敗的測試**

`AgentControllerTest`（`@SpringBootTest @AutoConfigureMockMvc @Import(AgentTestConfiguration.class)`）：
- `shouldReplyThroughAgent`：POST `{"message":"請分析卡號 4000000000000002"}` → 200，`$.reply == "stub reply"`。
- `shouldRejectBlankMessage`：POST `{"message":" "}` → 400。

- [ ] **Step 2: 執行測試，確認失敗**

Run: `./mvnw -pl agent-server test -Dtest=AgentControllerTest`
Expected: FAIL（404）。

- [ ] **Step 3: 實作 Controller**

- [ ] **Step 4: 執行測試，確認通過**

Run: `./mvnw -pl agent-server test`
Expected: PASS（6 tests）。

- [ ] **Step 5: 製作 `index.html`（經辦畫面）**

- 兩個區塊。
  - 「新戶核卡初審」：下拉選單列出 A100000001～A100000007（顯示姓名與情境），送出訊息 `請依 SOP 審核新戶核卡申請，客戶編號 {id}`。
  - 「異常刷卡封控」：下拉選單列出卡號 0001～0006（顯示情境），送出訊息 `請依 SOP 分析卡號 {cardNumber} 的近期交易並處置`。
- 另有自由輸入框。
- 回覆區使用 `whitespace-pre-wrap`；回覆以 `【緊急警告】` 開頭時，顯示 STYLE.md 第九節的紅色橫幅。
- 等待回覆時顯示 loading，並停用送出按鈕。
- 頁首連結到 `supervisor.html`。

- [ ] **Step 6: 製作 `supervisor.html`（主管放行畫面）**

- 主管代號輸入框，預設 `SUPERVISOR_01`。
- 從 `http://localhost:8081/api/supervisor/approval-drafts?status=PENDING_REVIEW` 讀取草稿，以表格顯示客戶、姓名、卡別、額度、評估摘要、建立時間。
- 每列有「核准」與「駁回」按鈕。執行後顯示結果（核准時顯示新卡號）並重新載入清單；409 時顯示「此草稿已被處理」。

- [ ] **Step 7: Commit**（`feat: add agent chat API and demo pages`）

---

### Task 10: 端對端 Demo 驗證

**Files:**
- Modify: `CLAUDE.md` 第九節（只在實際指令與文件不符時修改）

- [ ] **Step 1: 啟動 PostgreSQL 與 mcp-server**

```bash
docker run -d --name credit-card-pg -p 5432:5432 -e POSTGRES_DB=credit_card_demo -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo postgres:17
DB_URL=jdbc:postgresql://localhost:5432/credit_card_demo DB_USERNAME=demo DB_PASSWORD=demo ./mvnw -pl mcp-server spring-boot:run
```

Expected: log 顯示 Tomcat 在 8081 啟動，且註冊了 6 個 tools。

- [ ] **Step 2: 啟動 agent-server**

需要使用者提供 `GOOGLE_CLOUD_PROJECT`、`GOOGLE_CLOUD_LOCATION`、`VERTEX_AI_MODEL`。

Expected: 8080 啟動，MCP client 初始化成功。

- [ ] **Step 3: 逐一驗證 demo 情境**

用 `curl -s localhost:8080/api/agent/chat -H 'Content-Type: application/json' -d '{"message":"..."}'` 送出 CLAUDE.md 第七節的 13 個情境，再用以下指令核對資料庫：

```bash
docker exec credit-card-pg psql -U demo -d credit_card_demo -c "SELECT target_id, action_type, operator FROM audit_logs ORDER BY log_id"
```

Expected：
- A3、A4、A5、A6 各有一筆 `APPROVAL_DRAFT`；A1、A2、A7 各有一筆 `APPLICATION_DECLINE`。
- 卡號 0002、0003、0004 有 `TEMP_FREEZE`；0005 有 `ESCALATION`，而且回覆以 `【緊急警告】` 開頭。
- 卡號 0001、0006 沒有任何寫入。
- `GUARDRAIL_BLOCKED` 應為 0 筆；若有，代表 SOP 需要調整。

- [ ] **Step 4: 驗證主管放行**

開啟 `http://localhost:8080/supervisor.html` 核准 A100000003 的草稿，確認 `credit_cards` 新增一張 `4999…` 開頭的卡。

- [ ] **Step 5: 修正文件並 Commit**（`docs: verify end-to-end demo flow`）
