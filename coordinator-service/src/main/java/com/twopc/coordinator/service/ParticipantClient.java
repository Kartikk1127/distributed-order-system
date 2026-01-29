package com.twopc.coordinator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twopc.common.protocol.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

// HTTP client to communicate with the participants
// sends PREPARE, COMMIT, ABORT messages
@Component
public class ParticipantClient {
    private static Logger logger = LoggerFactory.getLogger(ParticipantClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ParticipantClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public TransactionMessage sendPrepare(String participantUrl, String txnId, Map<String, Object> operationData) {
        try {
            TransactionMessage prepareMsg = TransactionMessage.prepare(txnId, "coordinator", operationData);

            String requestBody = objectMapper.writeValueAsString(prepareMsg);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(participantUrl + "/api/transaction/prepare"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            logger.debug("[{}] Sending PREPARE to {}", txnId, participantUrl);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                TransactionMessage voteMsg = objectMapper.readValue(response.body(), TransactionMessage.class);
                logger.info("[{}] Received vote from {}: {}", txnId, participantUrl, voteMsg.getMessageType());
                return voteMsg;
            } else {
                logger.error("[{}] PREPARE failed with status {}: {}", txnId, response.statusCode(), response.body());
                return TransactionMessage.voteNo(txnId, participantUrl, "HTTP error: " + + response.statusCode());
            }
        } catch (Exception e) {
            logger.error("[{}] Error sending PREPARE to {}", txnId, participantUrl, e);
            return TransactionMessage.voteNo(txnId, participantUrl, "Communication error: " + e.getMessage());
        }
    }

    // send commit message to a participant
    public void sendCommit(String participantUrl, String txnId) {
        try {
            TransactionMessage prepareMsg = TransactionMessage.commit(txnId, "coordinator");

            String requestBody = objectMapper.writeValueAsString(prepareMsg);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(participantUrl + "/api/transaction/commit"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            logger.debug("[{}] Sending COMMIT to {}", txnId, participantUrl);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("[{}] COMMIT acknowledged by {}", txnId, participantUrl);
            } else {
                logger.error("[{}] COMMIT failed with status {}: {}", txnId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("[{}] Error sending COMMIT to {}", txnId, participantUrl, e);
            // in production we must retry
        }
    }

    // send ABORT message to a participant
    public void sendAbort(String participantUrl, String txnId) {
        try {
            TransactionMessage prepareMsg = TransactionMessage.abort(txnId, "coordinator", "Transaction Aborted");

            String requestBody = objectMapper.writeValueAsString(prepareMsg);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(participantUrl + "/api/transaction/abort"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            logger.debug("[{}] Sending COMMIT to {}", txnId, participantUrl);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("[{}] ABORT acknowledged by {}", txnId, participantUrl);
            } else {
                logger.error("[{}] ABORT failed with status {}: {}", txnId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("[{}] Error sending ABORT to {}", txnId, participantUrl, e);
        }
    }

}
