package com.demo.creditcard.mcp.clerk;

public record ApplicantListItem(String customerId, String fullName, ApplicationStatus status, String statusLabel, boolean actionable) {

    public static ApplicantListItem of(ApplicantSnapshot applicant, ApplicationStatus status) {
        return new ApplicantListItem(applicant.customerId(), applicant.fullName(), status, status.label(), status.isActionable());
    }
}
