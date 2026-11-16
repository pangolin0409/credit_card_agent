package com.demo.creditcard.mcp.fraudcontrol;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private final JdbcClient jdbcClient;

    public TransactionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TransactionRecord> findRecentByCardNumber(String cardNumber) {
        return jdbcClient.sql("""
                        SELECT transaction_id, transaction_time, amount, merchant_name, country_code, is_online
                          FROM transactions
                         WHERE card_number = :cardNumber
                           AND transaction_time >= (
                               SELECT MAX(transaction_time) - INTERVAL '24 hours'
                                 FROM transactions
                                WHERE card_number = :cardNumber)
                         ORDER BY transaction_time
                        """)
                .param("cardNumber", cardNumber)
                .query((resultSet, rowNumber) -> new TransactionRecord(
                        resultSet.getString("transaction_id"),
                        resultSet.getTimestamp("transaction_time").toLocalDateTime(),
                        resultSet.getInt("amount"),
                        resultSet.getString("merchant_name"),
                        resultSet.getString("country_code"),
                        resultSet.getBoolean("is_online")))
                .list();
    }
}
