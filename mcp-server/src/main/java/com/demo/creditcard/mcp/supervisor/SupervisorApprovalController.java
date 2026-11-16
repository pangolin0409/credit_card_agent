package com.demo.creditcard.mcp.supervisor;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.creditcard.mcp.cardapplication.ApprovalDraft;
import com.demo.creditcard.mcp.cardapplication.DraftStatus;

@RestController
@RequestMapping("/api/supervisor/approval-drafts")
public class SupervisorApprovalController {

    private final SupervisorApprovalService supervisorApprovalService;

    public SupervisorApprovalController(SupervisorApprovalService supervisorApprovalService) {
        this.supervisorApprovalService = supervisorApprovalService;
    }

    @GetMapping
    public List<ApprovalDraft> listDrafts(@RequestParam(defaultValue = "PENDING_REVIEW") DraftStatus status) {
        return supervisorApprovalService.findDrafts(status);
    }

    @PostMapping("/{draftId}/approve")
    public ReviewResult approve(@PathVariable int draftId, @Valid @RequestBody ReviewRequest request) {
        return supervisorApprovalService.approve(draftId, request.supervisorId());
    }

    @PostMapping("/{draftId}/reject")
    public ReviewResult reject(@PathVariable int draftId, @Valid @RequestBody ReviewRequest request) {
        return supervisorApprovalService.reject(draftId, request.supervisorId());
    }
}
