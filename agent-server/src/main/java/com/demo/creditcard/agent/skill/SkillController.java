package com.demo.creditcard.agent.skill;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkillController {

    private final SkillCatalog skillCatalog;

    public SkillController(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
    }

    @GetMapping("/api/agent/skills")
    public List<SkillSummary> listSkills() {
        return skillCatalog.all().stream().map(SkillSummary::from).toList();
    }
}
