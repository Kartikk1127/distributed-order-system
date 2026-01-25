package com.twopc.common.log;

import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.TransactionState;

import java.util.List;
import java.util.Optional;

// coordinator logs commit before sending, can recover decision if crashes
// participant logs prepared before voting yes, remembers the promise
public interface WriteAheadLog {
    void writeLog(Transaction transaction);
    Optional<Transaction> readLog(String transactionId);
    List<Transaction> readLogsByState(TransactionState state);
    List<Transaction> readAllLogs();
    void deleteLog(String transactionId);
    String getLogFilePath();

    class LogException extends RuntimeException {
        public LogException(String message, Throwable cause) {
            super(message, cause);
        }

        public LogException(String message) {
            super(message);
        }
    }
}
