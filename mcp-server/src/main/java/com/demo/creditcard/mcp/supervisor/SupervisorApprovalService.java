package com.demo.creditcard.mcp.supervisor;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.audit.AuditActionType;
import com.demo.creditcard.mcp.audit.AuditLogRepository;
import com.demo.creditcard.mcp.card.CreditCardRepository;
import com.demo.creditcard.mcp.cardapplication.ApprovalDraft;
import com.demo.creditcard.mcp.cardapplication.ApprovalDraftRepository;
import com.demo.creditcard.mcp.cardapplication.DraftStatus;

@Service
public class SupervisorApprovalService {

    private static final String ISSUED_CARD_NUMBER_FORMAT = "4999%012d";

    private final ApprovalDraftRepository approvalDraftRepository;
    private final CreditCardRepository creditCardRepository;
    private final AuditLogRepository auditLogRepository;

    public SupervisorApprovalService(ApprovalDraftRepository approvalDraftRepository, CreditCardRepository creditCardRepository,
            AuditLogRepository auditLogRepository) {
        this.approvalDraftRepository = approvalDraftRepository;
        this.creditCardRepository = creditCardRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<ApprovalDraft> findDrafts(DraftStatus status) {
        return approvalDraftRepository.findByStatus(status);
    }

    @Transactional
    public ReviewResult approve(int draftId, String supervisorId) {
        var draft = findPendingDraft(draftId);
        var cardNumber = ISSUED_CARD_NUMBER_FORMAT.formatted(draftId);

        creditCardRepository.insertActiveCard(cardNumber, draft.customerId(), draft.cardType(), draft.creditLimit());
        approvalDraftRepository.markReviewed(draftId, DraftStatus.APPROVED, supervisorId);
        auditLogRepository.append(draft.customerId(), AuditActionType.DRAFT_APPROVED,
                "draftId=%d 核准，發卡 %s（%s %,d）".formatted(draftId, cardNumber, draft.cardType(), draft.creditLimit()),
                supervisorId);
        return new ReviewResult(draftId, DraftStatus.APPROVED, cardNumber);
    }

    @Transactional
    public ReviewResult reject(int draftId, String supervisorId) {
        var draft = findPendingDraft(draftId);

        approvalDraftRepository.markReviewed(draftId, DraftStatus.REJECTED, supervisorId);
        auditLogRepository.append(draft.customerId(), AuditActionType.DRAFT_REJECTED, "draftId=%d 駁回".formatted(draftId),
                supervisorId);
        return new ReviewResult(draftId, DraftStatus.REJECTED, null);
    }

    private ApprovalDraft findPendingDraft(int draftId) {
        var draft = approvalDraftRepository.findById(draftId).orElseThrow(() -> new DraftNotFoundException(draftId));
        if (draft.status() != DraftStatus.PENDING_REVIEW) {
            throw new DraftAlreadyReviewedException(draftId, draft.status());
        }
        return draft;
    }
}
