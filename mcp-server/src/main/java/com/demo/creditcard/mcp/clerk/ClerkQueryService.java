package com.demo.creditcard.mcp.clerk;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.fraudcontrol.FraudControlService;
import com.demo.creditcard.mcp.fraudcontrol.FraudVerdict;

@Service
public class ClerkQueryService {

    private final ClerkQueryRepository clerkQueryRepository;
    private final ClerkStatusPolicy clerkStatusPolicy;
    private final FraudControlService fraudControlService;

    public ClerkQueryService(ClerkQueryRepository clerkQueryRepository, ClerkStatusPolicy clerkStatusPolicy,
            FraudControlService fraudControlService) {
        this.clerkQueryRepository = clerkQueryRepository;
        this.clerkStatusPolicy = clerkStatusPolicy;
        this.fraudControlService = fraudControlService;
    }

    @Transactional(readOnly = true)
    public List<ApplicantListItem> listApplications() {
        return toApplicantItems(clerkQueryRepository.findApplicants());
    }

    @Transactional(readOnly = true)
    public List<CardListItem> listAlertCards() {
        return clerkQueryRepository.findCards().stream()
                .map(card -> new AssessedCard(card, verdictOf(card)))
                .filter(assessed -> clerkStatusPolicy.isAlertCard(assessed.card(), assessed.verdict()))
                .map(this::toCardItem)
                .toList();
    }

    private List<ApplicantListItem> toApplicantItems(List<ApplicantSnapshot> applicants) {
        return applicants.stream()
                .map(applicant -> ApplicantListItem.of(applicant, clerkStatusPolicy.resolveApplicationStatus(applicant)))
                .toList();
    }

    private FraudVerdict verdictOf(CardSnapshot card) {
        return fraudControlService.getCardRiskProfile(card.cardNumber()).assessment().verdict();
    }

    private CardListItem toCardItem(AssessedCard assessed) {
        var card = assessed.card();
        return CardListItem.of(card, assessed.verdict(), clerkStatusPolicy.resolveCardHandlingStatus(card));
    }

    private record AssessedCard(CardSnapshot card, FraudVerdict verdict) {
    }
}
