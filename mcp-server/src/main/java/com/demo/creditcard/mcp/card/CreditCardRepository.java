package com.demo.creditcard.mcp.card;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.demo.creditcard.mcp.cardapplication.CardTier;

@Repository
public class CreditCardRepository {

    private final JdbcClient jdbcClient;

    public CreditCardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<CardRiskSubject> findRiskSubject(String cardNumber) {
        return jdbcClient.sql("""
                        SELECT cc.card_number, cc.customer_id, c.full_name, cc.status, c.has_travel_notice, c.travel_country
                          FROM credit_cards cc
                          JOIN customers c ON c.customer_id = cc.customer_id
                         WHERE cc.card_number = :cardNumber
                        """)
                .param("cardNumber", cardNumber)
                .query((resultSet, rowNumber) -> new CardRiskSubject(
                        resultSet.getString("card_number"),
                        resultSet.getString("customer_id"),
                        resultSet.getString("full_name"),
                        CardStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("has_travel_notice"),
                        resultSet.getString("travel_country")))
                .optional();
    }

    public void freeze(String cardNumber, String freezeReason) {
        jdbcClient.sql("""
                        UPDATE credit_cards
                           SET status = 'TEMP_FROZEN',
                               freeze_reason = :freezeReason
                         WHERE card_number = :cardNumber
                        """)
                .param("freezeReason", freezeReason)
                .param("cardNumber", cardNumber)
                .update();
    }

    public void insertActiveCard(String cardNumber, String customerId, CardTier cardType, int creditLimit) {
        jdbcClient.sql("""
                        INSERT INTO credit_cards (card_number, customer_id, card_type, credit_limit, status)
                        VALUES (:cardNumber, :customerId, :cardType, :creditLimit, 'ACTIVE')
                        """)
                .param("cardNumber", cardNumber)
                .param("customerId", customerId)
                .param("cardType", cardType.name())
                .param("creditLimit", creditLimit)
                .update();
    }
}
