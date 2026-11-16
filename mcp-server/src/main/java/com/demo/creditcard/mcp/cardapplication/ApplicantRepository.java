package com.demo.creditcard.mcp.cardapplication;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicantRepository {

    private final JdbcClient jdbcClient;

    public ApplicantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ApplicantProfile> findById(String customerId) {
        return jdbcClient.sql("""
                        SELECT customer_id, full_name, monthly_income, existing_unsecured_debt, credit_score, is_aml_blacklist
                          FROM customers
                         WHERE customer_id = :customerId
                        """)
                .param("customerId", customerId)
                .query((resultSet, rowNumber) -> new ApplicantProfile(
                        resultSet.getString("customer_id"),
                        resultSet.getString("full_name"),
                        resultSet.getInt("monthly_income"),
                        resultSet.getInt("existing_unsecured_debt"),
                        resultSet.getInt("credit_score"),
                        resultSet.getBoolean("is_aml_blacklist")))
                .optional();
    }
}
