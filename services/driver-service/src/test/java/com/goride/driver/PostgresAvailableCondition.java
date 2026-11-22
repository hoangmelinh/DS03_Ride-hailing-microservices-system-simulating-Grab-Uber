package com.goride.driver;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.net.Socket;

public class PostgresAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("PostgreSQL is reachable");
    private static final ConditionEvaluationResult DISABLED = ConditionEvaluationResult.disabled("PostgreSQL is not reachable on localhost:5433. Skipping test requiring live database.");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5433), 300);
            return ENABLED;
        } catch (Exception e) {
            return DISABLED;
        }
    }
}

