package com.demo.creditcard.mcp.fraudcontrol;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.audit.AuditActionType;
import com.demo.creditcard.mcp.audit.AuditLogRepository;
import com.demo.creditcard.mcp.card.CardNotFoundException;
import com.demo.creditcard.mcp.card.CardRiskSubject;
import com.demo.creditcard.mcp.card.CardStatus;
import com.demo.creditcard.mcp.card.CreditCardRepository;
import com.demo.creditcard.mcp.shared.ToolOutcome;

@Service
public class FraudControlService {

    private static final Set<FraudVerdict> FREEZABLE_VERDICTS = Set.of(FraudVerdict.OVERSEAS_FRAUD, FraudVerdict.CARD_TESTING);

    private final CreditCardRepository creditCardRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final FraudDetectionPolicy fraudDetectionPolicy;

    public FraudControlService(CreditCardRepository creditCardRepository, TransactionRepository transactionRepository,
            AuditLogRepository auditLogRepository, FraudDetectionPolicy fraudDetectionPolicy) {
        this.creditCardRepository = creditCardRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.fraudDetectionPolicy = fraudDetectionPolicy;
    }

    @Transactional(readOnly = true)
    public CardRiskProfile getCardRiskProfile(String cardNumber) {
        var card = findCard(cardNumber);
        var transactions = transactionRepository.findRecentByCardNumber(cardNumber);
        return new CardRiskProfile(card, transactions, fraudDetectionPolicy.assess(card, transactions));
    }

    @Transactional
    public ToolOutcome freezeCard(String cardNumber, String freezeReason) {
        var card = findCard(cardNumber);

        if (card.status() != CardStatus.ACTIVE) {
            return block(cardNumber, "卡片目前狀態為 %s，只有 ACTIVE 的卡片可以暫時凍結。".formatted(card.status()));
        }
        var verdict = fraudDetectionPolicy.assess(card, transactionRepository.findRecentByCardNumber(cardNumber)).verdict();
        if (verdict == FraudVerdict.ESCALATE_TO_RISK_OFFICER) {
            return block(cardNumber, "近期有單筆超過 500,000 的交易，AI 不得鎖卡，請改用 escalateToRiskOfficer 升級資深風控主管。");
        }
        if (!FREEZABLE_VERDICTS.contains(verdict)) {
            return block(cardNumber, "規則引擎判定為 %s，不符合暫時凍結條件。".formatted(verdict));
        }

        creditCardRepository.freeze(cardNumber, freezeReason);
        auditLogRepository.append(cardNumber, AuditActionType.TEMP_FREEZE, freezeReason, AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.accepted("卡片 %s 已設為 TEMP_FROZEN（暫時凍結）。".formatted(cardNumber));
    }

    @Transactional
    public ToolOutcome escalateToRiskOfficer(String cardNumber, String caseSummary) {
        findCard(cardNumber);
        auditLogRepository.append(cardNumber, AuditActionType.ESCALATION, caseSummary, AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.accepted("卡片 %s 已升級資深風控主管人工處置，卡片狀態未變更。".formatted(cardNumber));
    }

    private CardRiskSubject findCard(String cardNumber) {
        return creditCardRepository.findRiskSubject(cardNumber).orElseThrow(() -> new CardNotFoundException(cardNumber));
    }

    private ToolOutcome block(String cardNumber, String reason) {
        auditLogRepository.append(cardNumber, AuditActionType.GUARDRAIL_BLOCKED, "freezeCard 被拒：" + reason,
                AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.rejected(reason);
    }
}
