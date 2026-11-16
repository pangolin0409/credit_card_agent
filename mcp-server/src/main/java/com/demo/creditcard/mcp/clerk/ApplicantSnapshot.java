package com.demo.creditcard.mcp.clerk;

import java.util.Optional;

import com.demo.creditcard.mcp.cardapplication.DraftStatus;

public record ApplicantSnapshot(String customerId, String fullName, Optional<DraftStatus> latestDraftStatus, boolean hasDecline) {
}
