package com.demo.creditcard.mcp.supervisor;

import com.demo.creditcard.mcp.cardapplication.DraftStatus;

public class DraftAlreadyReviewedException extends RuntimeException {

    public DraftAlreadyReviewedException(int draftId, DraftStatus status) {
        super("Approval draft %d has already been reviewed: %s".formatted(draftId, status));
    }
}
