package com.demo.creditcard.mcp.fraudcontrol;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.demo.creditcard.mcp.card.CardRiskSubject;

@Component
public class FraudDetectionPolicy {

    private static final String DOMESTIC_COUNTRY_CODE = "TW";
    private static final int ESCALATION_SINGLE_AMOUNT_THRESHOLD = 500_000;
    private static final int OVERSEAS_FRAUD_AMOUNT_THRESHOLD = 50_000;
    private static final Duration OVERSEAS_FRAUD_WINDOW = Duration.ofHours(1);
    private static final int MICRO_TRANSACTION_MAX_AMOUNT = 50;
    private static final int CARD_TESTING_MIN_COUNT = 3;
    private static final Duration CARD_TESTING_WINDOW = Duration.ofMinutes(10);

    public FraudAssessment assess(CardRiskSubject subject, List<TransactionRecord> transactions) {
        var metrics = measure(subject, transactions);

        if (metrics.maxSingleAmount() > ESCALATION_SINGLE_AMOUNT_THRESHOLD) {
            return new FraudAssessment(FraudVerdict.ESCALATE_TO_RISK_OFFICER, metrics,
                    "單筆交易 %,d 超過 500,000 資安紅線，AI 不得鎖卡，須立即升級資深風控主管人工處置。"
                            .formatted(metrics.maxSingleAmount()));
        }
        if (isMatchingTravel(subject, transactions)) {
            return new FraudAssessment(FraudVerdict.NORMAL_TRAVEL, metrics,
                    "持卡人已登錄出國旅遊（%s），國外交易國家皆相符，判定為正常出國消費，建議經辦解除警報。"
                            .formatted(subject.travelCountry()));
        }
        if (metrics.foreignOnlineAmountWithinOneHour() > OVERSEAS_FRAUD_AMOUNT_THRESHOLD) {
            return new FraudAssessment(FraudVerdict.OVERSEAS_FRAUD, metrics,
                    "未登錄相符的出國通知，最新交易往回 1 小時內境外網路消費累計 %,d，超過 50,000，高度疑似盜刷。"
                            .formatted(metrics.foreignOnlineAmountWithinOneHour()));
        }
        if (metrics.microTransactionCountWithinTenMinutes() >= CARD_TESTING_MIN_COUNT) {
            return new FraudAssessment(FraudVerdict.CARD_TESTING, metrics,
                    "最新交易往回 10 分鐘內出現 %d 筆 50 元以下交易，符合測卡攻擊特徵。"
                            .formatted(metrics.microTransactionCountWithinTenMinutes()));
        }
        return new FraudAssessment(FraudVerdict.NO_ANOMALY, metrics, "未命中任何異常規則。");
    }

    public boolean exceedsEscalationThreshold(List<TransactionRecord> transactions) {
        return maxSingleAmount(transactions) > ESCALATION_SINGLE_AMOUNT_THRESHOLD;
    }

    private RiskMetrics measure(CardRiskSubject subject, List<TransactionRecord> transactions) {
        if (transactions.isEmpty()) {
            return new RiskMetrics(0, 0, 0);
        }
        var latest = latestTransactionTime(transactions);
        return new RiskMetrics(
                maxSingleAmount(transactions),
                sumForeignOnlineAmount(subject, transactions, latest.minus(OVERSEAS_FRAUD_WINDOW)),
                countMicroTransactions(transactions, latest.minus(CARD_TESTING_WINDOW)));
    }

    private static LocalDateTime latestTransactionTime(List<TransactionRecord> transactions) {
        return transactions.stream()
                .map(TransactionRecord::transactionTime)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    private static int maxSingleAmount(List<TransactionRecord> transactions) {
        return transactions.stream().mapToInt(TransactionRecord::amount).max().orElse(0);
    }

    private static int sumForeignOnlineAmount(CardRiskSubject subject, List<TransactionRecord> transactions, LocalDateTime windowStart) {
        return transactions.stream()
                .filter(transaction -> !transaction.transactionTime().isBefore(windowStart))
                .filter(TransactionRecord::isOnline)
                .filter(FraudDetectionPolicy::isForeign)
                .filter(transaction -> !Objects.equals(transaction.countryCode(), subject.travelCountry()))
                .mapToInt(TransactionRecord::amount)
                .sum();
    }

    private static int countMicroTransactions(List<TransactionRecord> transactions, LocalDateTime windowStart) {
        return (int) transactions.stream()
                .filter(transaction -> !transaction.transactionTime().isBefore(windowStart))
                .filter(transaction -> transaction.amount() <= MICRO_TRANSACTION_MAX_AMOUNT)
                .count();
    }

    private static boolean isMatchingTravel(CardRiskSubject subject, List<TransactionRecord> transactions) {
        var foreignTransactions = transactions.stream().filter(FraudDetectionPolicy::isForeign).toList();
        return subject.hasTravelNotice()
                && !foreignTransactions.isEmpty()
                && foreignTransactions.stream().allMatch(transaction -> transaction.countryCode().equals(subject.travelCountry()));
    }

    private static boolean isForeign(TransactionRecord transaction) {
        return !DOMESTIC_COUNTRY_CODE.equals(transaction.countryCode());
    }
}
