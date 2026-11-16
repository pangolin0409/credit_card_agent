package com.demo.creditcard.mcp.cardapplication;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.audit.AuditActionType;
import com.demo.creditcard.mcp.audit.AuditLogRepository;
import com.demo.creditcard.mcp.shared.ToolOutcome;

@Service
public class CardApplicationService {

    private final ApplicantRepository applicantRepository;
    private final ApprovalDraftRepository approvalDraftRepository;
    private final AuditLogRepository auditLogRepository;
    private final CardApplicationPolicy cardApplicationPolicy;

    public CardApplicationService(ApplicantRepository applicantRepository, ApprovalDraftRepository approvalDraftRepository,
            AuditLogRepository auditLogRepository, CardApplicationPolicy cardApplicationPolicy) {
        this.applicantRepository = applicantRepository;
        this.approvalDraftRepository = approvalDraftRepository;
        this.auditLogRepository = auditLogRepository;
        this.cardApplicationPolicy = cardApplicationPolicy;
    }

    @Transactional(readOnly = true)
    public ApplicantProfileResult getApplicantProfile(String customerId) {
        var applicant = findApplicant(customerId);
        return new ApplicantProfileResult(applicant, cardApplicationPolicy.assess(applicant));
    }

    @Transactional
    public ToolOutcome createApprovalDraft(ApprovalDraftRequest request) {
        var assessment = cardApplicationPolicy.assess(findApplicant(request.customerId()));

        if (approvalDraftRepository.existsPendingByCustomerId(request.customerId())) {
            return block(request.customerId(), "該申請人已有待主管審核的草稿單，不得重複建立。");
        }
        var violation = cardApplicationPolicy.findDraftViolation(assessment, request.cardTier(), request.creditLimit());
        if (violation.isPresent()) {
            return block(request.customerId(), violation.get());
        }

        int draftId = approvalDraftRepository.insert(request);
        auditLogRepository.append(request.customerId(), AuditActionType.APPROVAL_DRAFT,
                "draftId=%d, %s %,d：%s".formatted(draftId, request.cardTier(), request.creditLimit(), request.assessmentSummary()),
                AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.accepted("已建立初審核卡草稿單 #%d，待主管放行。".formatted(draftId));
    }

    @Transactional
    public ToolOutcome declineApplication(String customerId, String reason) {
        findApplicant(customerId);
        auditLogRepository.append(customerId, AuditActionType.APPLICATION_DECLINE, reason, AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.accepted("已記錄申請人 %s 的退件原因。".formatted(customerId));
    }

    private ApplicantProfile findApplicant(String customerId) {
        return applicantRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private ToolOutcome block(String customerId, String reason) {
        auditLogRepository.append(customerId, AuditActionType.GUARDRAIL_BLOCKED, "createApprovalDraft 被拒：" + reason,
                AuditLogRepository.AI_OPERATOR);
        return ToolOutcome.rejected(reason);
    }
}
