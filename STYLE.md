# STYLE.md

本專案嚴格遵守 Clean Code。規則用中文說明，程式命名與指令一律使用英文。

---

## 一、命名

- 名稱要能說明意圖，禁止縮寫（`customer` 不寫 `cust`，`transaction` 不寫 `txn`）。業界通用縮寫例外：`id`、`url`、`dto` 不用，`aml` 可用。
- 類別用名詞（`CardApplicationPolicy`），方法用動詞開頭（`calculateRemainingDebtCapacity`）。
- boolean 以 `is` / `has` / `can` 開頭（`isAmlBlacklisted`、`hasTravelNotice`）。
- 常數命名要帶出業務意義，數字不得直接出現在邏輯中：

```java
private static final int DEBT_BURDEN_MULTIPLIER = 22;
private static final int MIN_REMAINING_DEBT_CAPACITY = 100_000;
private static final int ESCALATION_SINGLE_AMOUNT_THRESHOLD = 500_000;
```

- 套件依功能切分（`cardapplication`、`fraudcontrol`、`supervisor`、`audit`），不依技術層切分（禁止 `controller/`、`service/` 這類頂層套件）。

| 角色 | 命名後綴 | 範例 |
|---|---|---|
| MCP 工具類別 | `Tools` | `FraudControlTools` |
| 業務規則（純邏輯） | `Policy` | `FraudDetectionPolicy` |
| 流程協調 | `Service` | `CardFreezeService` |
| 資料存取 | `Repository` | `TransactionRepository` |
| REST 端點 | `Controller` | `SupervisorApprovalController` |

## 二、單一職責與分層

```
Tools / Controller  →  Service  →  Policy（純函式，無 I/O）
                            ↘  Repository（JdbcClient）
```

- `Tools` 與 `Controller` 只負責轉換輸入輸出並委派給 Service，不寫業務判斷。
- `Policy` 不注入任何 Bean、不碰資料庫，輸入 record、輸出 record，方便單元測試。
- `Repository` 一個方法只做一條 SQL。
- 一個方法只做一件事，原則上不超過 20 行，參數不超過 3 個；超過時改用 record 包裝。

## 三、record、enum 與型別

- DTO、Tool 的輸入輸出、Policy 的評估結果、資料列對應全部使用 `record`。
- 狀態與代碼一律使用 `enum`，資料庫存 `name()`：

```java
public enum CardTier {
    INFINITE(500_000),
    SIGNATURE(150_000);

    private final int maximumCreditLimit;

    CardTier(int maximumCreditLimit) {
        this.maximumCreditLimit = maximumCreditLimit;
    }

    public int maximumCreditLimit() {
        return maximumCreditLimit;
    }
}
```

- 金額使用 `int`（新台幣整數元），禁止 `float` / `double`。
- 時間使用 `LocalDateTime`（對應 `TIMESTAMP`）。
- 可能不存在的查詢結果回傳 `Optional<T>`，禁止回傳 `null`。
- 型別在右側一目瞭然時可以用 `var`。
- 不使用 Lombok。

## 四、註解

- 不寫註解。程式要靠命名與小方法自我說明。
- 不寫 Javadoc，只有以下兩種例外：
  - `@Tool(description = ...)` 與 `@ToolParam(description = ...)`：這是給 LLM 讀的介面契約，用英文寫，說明用途、前置條件與拒絕情形。
  - 外部限制造成的非直覺寫法：用一行說明原因。
- 禁止留下被註解掉的程式碼與 `TODO`。

## 五、Spring 與 Spring AI

- 一律使用建構子注入，欄位宣告為 `private final`；禁止在欄位上加 `@Autowired`。
- 寫入型 `@Tool` 執行前必須呼叫對應的 Policy 驗證護欄。被拒絕時**回傳**結果 record（不要拋例外），讓 LLM 能讀到拒絕原因：

```java
public record ToolOutcome(boolean accepted, String message) {

    public static ToolOutcome accepted(String message) {
        return new ToolOutcome(true, message);
    }

    public static ToolOutcome rejected(String reason) {
        return new ToolOutcome(false, reason);
    }
}
```

- 所有寫入型 Tool 與主管 API 的「業務寫入」和「稽核寫入」要放在同一個 `@Transactional` 方法內。
- 主管放行相關功能不可加上 `@Tool`。
- SOP 以 `Resource` 從 `classpath:skills/credit_card_sop.md` 載入，設為 `ChatClient` 的 default system prompt；禁止把 SOP 內容寫死在 Java 字串中。

## 六、SQL

- 關鍵字大寫，資料表與欄位用 snake_case。
- 約束一定要命名：`pk_<table>`、`fk_<table>_<ref>`、`ck_<table>_<column>`。
- `INSERT` 必須明確列出欄位清單。
- Java 中的 SQL 使用 text block，參數一律使用具名參數綁定，禁止字串串接：

```java
jdbcClient.sql("""
        UPDATE credit_cards
           SET status = 'TEMP_FROZEN',
               freeze_reason = :freezeReason
         WHERE card_number = :cardNumber
        """)
        .param("freezeReason", freezeReason)
        .param("cardNumber", cardNumber)
        .update();
```

- DDL 與種子 DML 的留存規範見 [CLAUDE.md](CLAUDE.md) 第六節。

## 七、application.properties

- 只使用 `.properties`，key=value 寫法，key 用 kebab-case。
- 依功能分組，組與組之間空一行。
- 密碼、專案 ID、連線字串一律用 `${ENV_VAR}` 帶入，禁止寫死。

```properties
server.port=8081

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.sql.init.mode=always

spring.ai.mcp.server.name=credit-card-mcp-server
spring.ai.mcp.server.protocol=STREAMABLE
```

## 八、測試（TDD）

- 嚴格遵守紅燈 → 綠燈 → 重構。沒有先看到測試失敗，就不寫實作程式。
- 測試方法用英文 `should<ExpectedBehavior>When<Condition>` 命名，並用 `@DisplayName` 以中文寫出對應的業務規則：

```java
@Test
@DisplayName("單筆超過 50 萬時，freezeCard 拒絕鎖卡並寫入 GUARDRAIL_BLOCKED")
void shouldRejectFreezeWhenSingleTransactionExceedsEscalationThreshold() {
```

- 測試內容用空行分成 given / when / then 三段，不寫段落註解。
- 斷言使用 AssertJ。
- 測試分工：
  - Policy 寫純單元測試，覆蓋每條規則的邊界值（例如剛好 100,000、剛好 50 元、剛好 3 筆）。
  - MCP Tool 寫整合測試，使用 Testcontainers PostgreSQL 搭配 `@ServiceConnection`，驗證資料庫狀態與 `audit_logs` 都正確寫入。
  - Agent Server 不呼叫真實 Vertex AI。驗證 SOP 已載入 system prompt、註冊的工具清單正好等於 CLAUDE.md 第五節的 6 個 Tool，且不含任何主管放行功能。
- 測試案例以 [CLAUDE.md](CLAUDE.md) 第七節的 demo 情境為準。

## 九、前端（HTML + Tailwind）

- 每個畫面一個 HTML 檔，放在 `agent-server/src/main/resources/static/`。
- Tailwind 使用 CDN，不引入任何前端框架或建置工具；JavaScript 使用原生 `fetch` 與 `async/await`，寫在頁面底部的 `<script>`。
- JavaScript 命名同樣遵守語意化原則：函式用動詞開頭，用 `const` / `let`，禁止 `var`。
- 卡片與草稿單狀態的顏色要一致：

| 狀態 | 顏色 |
|---|---|
| `ACTIVE` / `APPROVED` | green |
| `PENDING_REVIEW` | amber |
| `TEMP_FROZEN` / `REJECTED` | red |
| 紅線升級警告 | 紅底白字橫幅，固定在回覆最上方 |
