package com.demo.creditcard.mcp.clerk;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clerk")
public class ClerkQueryController {

    private final ClerkQueryService clerkQueryService;

    public ClerkQueryController(ClerkQueryService clerkQueryService) {
        this.clerkQueryService = clerkQueryService;
    }

    @GetMapping("/applications")
    public List<ApplicantListItem> listApplications() {
        return clerkQueryService.listApplications();
    }

    @GetMapping("/alert-cards")
    public List<CardListItem> listAlertCards() {
        return clerkQueryService.listAlertCards();
    }

    @GetMapping("/applicants/search")
    public List<ApplicantListItem> searchApplicants(@RequestParam(defaultValue = "") String q) {
        return clerkQueryService.searchApplicants(q);
    }

    @GetMapping("/cards/search")
    public List<CardListItem> searchCards(@RequestParam(defaultValue = "") String q) {
        return clerkQueryService.searchCards(q);
    }
}
