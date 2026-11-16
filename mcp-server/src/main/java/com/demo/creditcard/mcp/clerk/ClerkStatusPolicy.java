package com.demo.creditcard.mcp.clerk;

import org.springframework.stereotype.Component;

import com.demo.creditcard.mcp.card.CardStatus;
import com.demo.creditcard.mcp.fraudcontrol.FraudVerdict;

@Component
public class ClerkStatusPolicy {

    public ApplicationStatus resolveApplicationStatus(ApplicantSnapshot applicant) {
        return applicant.latestDraftStatus()
                .map(draftStatus -> switch (draftStatus) {
                    case APPROVED -> ApplicationStatus.APPROVED;
                    case PENDING_REVIEW -> ApplicationStatus.PENDING_REVIEW;
                    case REJECTED -> ApplicationStatus.SUPERVISOR_REJECTED;
                })
                .orElseGet(() -> applicant.hasDecline() ? ApplicationStatus.DECLINED : ApplicationStatus.AWAITING_REVIEW);
    }

    public CardHandlingStatus resolveCardHandlingStatus(CardSnapshot card) {
        if (card.status() == CardStatus.TEMP_FROZEN) {
            return CardHandlingStatus.FROZEN;
        }
        return card.hasEscalation() ? CardHandlingStatus.ESCALATED : CardHandlingStatus.AWAITING_ACTION;
    }

    public boolean isAlertCard(CardSnapshot card, FraudVerdict verdict) {
        return verdict != FraudVerdict.NO_ANOMALY || card.status() != CardStatus.ACTIVE || card.hasEscalation();
    }
}
