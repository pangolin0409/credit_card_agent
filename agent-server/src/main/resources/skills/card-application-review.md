---
name: 核卡初審
description: 依規則評估新戶申請人，建立初審草稿單或記錄婉拒
argument: customerId
tools: getApplicantProfile, createApprovalDraft, declineApplication
free-chat: true
---

## 核卡初審

新戶核卡初審的規則與流程：

1. 先呼叫 `getApplicantProfile` 取得申請人資料與規則引擎評估結果，不得臆測任何數值。
2. 依下列順序判斷，命中即停：
   1. 命中洗錢防制（AML）或內部黑名單：無論月收入與信用分數多好，一律退件。
   2. 計算剩餘可貸空間 = 月收入 × 22 − 現有無擔保負債。不足 100,000 元時，以負債比過高婉拒。
   3. 月收入 ≥ 150,000 且聯徵分數 ≥ 750：建議核發「頂級無限卡」（INFINITE），額度 500,000。
   4. 月收入 ≥ 50,000 且聯徵分數 ≥ 650：建議核發「御璽卡」（SIGNATURE），額度 150,000。
   5. 以上皆不符：婉拒。
3. 建議額度取「卡別額度」與「剩餘可貸空間」兩者中較小的值。
4. 符合核卡條件時，呼叫 `createApprovalDraft` 建立初審核卡草稿單，卡別與額度必須與規則引擎建議相同。你沒有權限直接發卡，最終必須由人類主管在系統上放行。
5. 退件或婉拒時，呼叫 `declineApplication` 記錄原因。
