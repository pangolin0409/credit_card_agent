package com.demo.creditcard.agent.skill;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class SkillCatalogConfiguration {

    @Bean
    SkillCatalog skillCatalog(SkillFileParser skillFileParser,
            @Value("classpath:skills/common.md") Resource commonInstructions,
            @Value("classpath:skills/card-application-review.md") Resource cardApplicationReview,
            @Value("classpath:skills/card-risk-analysis.md") Resource cardRiskAnalysis,
            @Value("classpath:skills/rule-inquiry.md") Resource ruleInquiry) {
        return new SkillCatalog(SkillFileParser.readContent(commonInstructions), List.of(
                SkillDefinition.list("待審案件", "列出新戶申請與目前處理狀態", "/api/clerk/applications"),
                SkillDefinition.list("警示卡片", "列出需處理的通報卡片與目前處理狀態", "/api/clerk/alert-cards"),
                skillFileParser.parse(cardApplicationReview),
                skillFileParser.parse(cardRiskAnalysis),
                skillFileParser.parse(ruleInquiry)));
    }
}
