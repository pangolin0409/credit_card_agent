我有場景是信辦卡經辦使用 ai agent 可以對新戶核卡、對異常刷卡通報鎖卡，這是一個 demo 展示專案給 it 看的
我想要根據以下資訊寫 CLAUDE.md, STYLE.md 所有 DDL、DML 都要有留存下來。

場景一：新戶核卡初審
場景二：異常刷卡進行通報封鎖

## 一、新戶核卡初審規範
1. 只要系統顯示該申請人命中洗錢防制或內部黑名單，無論他的月收入多高、信用評分多好，AI 都會直接判定退件
2. 計算申請人的「月收入乘以 22」再扣除他目前的「現有負債」。如果算出來剩餘的可貸額度空間不足 10 萬元，就會因為負債比過高而直接婉拒
3. 根據財力與信用分數決定發哪張卡、給多少額度。如果月收入達 15 萬元以上且聯徵分數達 750 分以上，會建議核發「頂級無限卡」並給予 50 萬元額度；如果月收入達 5 萬元以上且聯徵分數達 650 分以上，則建議核發「御璽卡」並給予 15 萬元額度；若未達上述門檻則予以婉拒
4. 即使完全符合核卡條件，AI 也沒有權限直接把卡片狀態寫入正式發卡資料表，它只能呼叫工具建立一張「初審核卡草稿單」，最終的核准按鈕必須交由人類主管在系統上放行。


## 二、異常刷卡封控規範
1. 出國旅遊正常消費排除：當發現國外大額刷卡時，AI 會先比對持卡人是否有在網銀登錄出國旅遊通知。如果有登錄，且刷卡的國家與登錄的旅遊國家吻合，AI 就會判定這是正常的出國消費，不會誤鎖卡片，只會建議經辦解除警報。
2. 未出國境外大額盜刷鎖卡：如果持卡人完全沒有登錄出國通知，但系統卻在 1 小時內偵測到國外網路消費，且累積刷卡金額超過 5 萬元，AI 就會判定高度疑似盜刷，立刻呼叫工具將卡片設為「暫時凍結」狀態，阻斷後續盜刷損失。
3. 短時間小額測卡攻擊鎖卡：針對駭客常見的盜刷手法，如果 AI 在近期明細中發現同一張卡在 10 分鐘內連續出現 3 筆以上、金額在 50 元以下的測試性刷卡，即使總金額不大，AI 也會果斷執行「暫時凍結」來保護卡片。
4. 超高金額人工升級紅線：為了防止 AI 在重大案件上誤判，我們設定了一道資安紅線。只要發現單筆異常交易金額超過 50 萬元，AI 嚴禁擅自執行鎖卡動作，而是必須立刻向經辦發出緊急警告，將整理好的交易明細轉交給資深風控主管進行人工處置。

一、 技術棧與規範

• 後端架構：雙 Server 分離（AI Agent Server + MCP Server）。
• 環境框架：Java 25 + Spring Boot 4.1.2 + Spring AI MCP。
• 設定檔格式：application.properties（key=value 寫法）。
• 前端介面：純 HTML + Tailwind CSS。
• 資料庫：PostgreSQL。
• Coding Style：嚴格遵守 Clean Code（語意化命名、單一職責、使用 record、無多餘註解）。

二、 開發執行順序

1. 資料庫建置：建立 PostgreSQL schema.sql（客戶、卡片、交易、稽核表）與 data.sql（塞入核卡與封控測試資料）。
2. TDD 開發 MCP Server：先寫 Tool 整合測試（紅燈） → 實作 @Tool 與資料庫操作（綠燈） → 重構。
3. TDD 開發 AI Agent Server：先寫測試（驗證 Skill 載入與工具調度邊界） → 實作 credit_card_sop.md 與 Agent API → 重構。

三、資料庫

1. customers（客戶與信用主檔）

存放信用卡申請人與持卡人的基本財力、聯徵信用狀態、黑名單註記以及出國旅遊登錄資訊。

欄位名稱	資料型別	約束條件	欄位說明
customer_id	VARCHAR(20)	PRIMARY KEY	客戶身分證字號或唯一客戶編號
full_name	VARCHAR(50)	NOT NULL	客戶姓名
monthly_income	INT	NOT NULL	申報月收入（新台幣，用於計算 DBR 22 倍上限與核卡分級）
existing_unsecured_debt	INT	NOT NULL DEFAULT 0	聯徵中心查詢之現有無擔保負債總額（含信貸與他行卡債）
credit_score	INT	NOT NULL	聯徵中心信用評分（分數區間通常為 200～800 分）
is_aml_blacklist	BOOLEAN	NOT NULL DEFAULT FALSE	洗錢防制（AML）或行內黑名單註記（true 代表命中黑名單）
has_travel_notice	BOOLEAN	NOT NULL DEFAULT FALSE	是否已於網銀或客服登錄出國旅遊通知
travel_country	VARCHAR(10)	NULL	登錄的旅遊目的地國家代碼（ISO 兩碼，如 JP、US）


2. credit_cards（信用卡卡片主檔）

存放已核發的信用卡資料、信用額度以及卡片當前的風控狀態。

欄位名稱	資料型別	約束條件	欄位說明
card_number	VARCHAR(20)	PRIMARY KEY	信用卡卡號（遮罩或測試卡號格式）
customer_id	VARCHAR(20)	NOT NULL, FK	持卡人客戶編號（關聯 customers.customer_id）
card_type	VARCHAR(30)	NOT NULL	信用卡卡別（如 無限卡、御璽卡）
credit_limit	INT	NOT NULL	該卡片核准之信用額度（新台幣）
status	VARCHAR(20)	NOT NULL DEFAULT 'ACTIVE'	卡片狀態：ACTIVE（正常）、TEMP_FROZEN（暫時凍結）、CLOSED（永久停卡）
freeze_reason	TEXT	NULL	執行凍結或停卡時的異常原因紀錄


3. transactions（刷卡交易明細表）

存放信用卡近期授權與消費紀錄，供風控工具分析短時間高頻交易、跨國消費與測卡行為。

欄位名稱	資料型別	約束條件	欄位說明
transaction_id	VARCHAR(30)	PRIMARY KEY	交易授權序號
card_number	VARCHAR(20)	NOT NULL, FK	消費使用之信用卡卡號（關聯 credit_cards.card_number）
transaction_time	TIMESTAMP	NOT NULL	交易發生時間戳記
amount	INT	NOT NULL	折合新台幣之交易金額
merchant_name	VARCHAR(100)	NOT NULL	特約商店名稱
country_code	VARCHAR(10)	NOT NULL	交易發生地國家代碼（如 TW、US、JP、FR）
is_online	BOOLEAN	NOT NULL DEFAULT FALSE	是否為網路線上交易（CNP, Card-Not-Present）


4. audit_logs（系統稽核軌跡表）

記錄所有透過 MCP Tool 執行的寫入與決策動作（如建立核卡草稿、暫時鎖卡），確保操作具備不可否認性與資安可追溯性。

欄位名稱	資料型別	約束條件	欄位說明
log_id	SERIAL	PRIMARY KEY	自動遞增之稽核紀錄流水號
target_id	VARCHAR(30)	NOT NULL	操作目標識別碼（核卡草稿填 customer_id；鎖卡填 card_number）
action_type	VARCHAR(30)	NOT NULL	執行的動作類型（如 APPROVAL_DRAFT 核卡草稿、TEMP_FREEZE 暫時凍結）
reason	TEXT	NOT NULL	操作明細、評估結果或異常鎖卡原因
operator	VARCHAR(50)	NOT NULL DEFAULT 'AI_AGENT_MCP'	操作執行者識別標記
created_at	TIMESTAMP	NOT NULL DEFAULT CURRENT_TIMESTAMP	稽核紀錄寫入時間
