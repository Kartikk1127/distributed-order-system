package com.twopc.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class FileBasedWAL implements WriteAheadLog {
    private static final Logger logger = LoggerFactory.getLogger(FileBasedWAL.class);

    private final String logFilePath;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock;

    public FileBasedWAL(String serviceName, String baseDir) {
        this.logFilePath = baseDir + "/" + serviceName + "/wal.log";
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.lock = new ReentrantReadWriteLock();

        initializeLogFile();
    }

    private void initializeLogFile() {
        try {
            Path path = Paths.get(logFilePath);
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.createFile(path);
                logger.info("Created WAL file: {}", logFilePath);
            }
        } catch (IOException e) {
            throw new LogException("Failed to initialize WAL file: " + logFilePath, e);
        }
    }

    @Override
    public void writeLog(Transaction transaction) {
        lock.writeLock().lock();
        try {
            String jsonLine = objectMapper.writeValueAsString(transaction);

            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(logFilePath, true)
            )){
                writer.write(jsonLine);
                writer.newLine();
                writer.flush();
            }

            logger.debug("Wrote to WAL: {}", transaction);
        } catch (IOException e) {
            throw new LogException("Failed to write to WAL: " + transaction.getTransactionId(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Transaction> readLog(String transactionId) {
        lock.readLock().lock();
        try {
            List<Transaction> allTransactions = readAllTransactionsFromFile();
            return allTransactions.stream()
                    .filter(txn -> txn.getTransactionId().equals(transactionId))
                    .reduce((first, second) -> second);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Transaction> readLogsByState(TransactionState state) {
        lock.readLock().lock();
        try {
            List<Transaction> allTransactions = readAllTransactionsFromFile();
            return allTransactions.stream()
                    .filter(txn -> txn.getState().equals(state))
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Transaction> readAllTransactionsFromFile() {
        List<Transaction> transactions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Transaction txn = objectMapper.readValue(line, Transaction.class);
                    transactions.add(txn);
                }
            }
        } catch (FileNotFoundException e) {
            return transactions;
        } catch (IOException e) {
            throw new LogException("Failed to read from WAL", e);
        }
        return transactions;
    }

    private Map<String, Transaction> getLatestTransactionSnapshots() {
        List<Transaction> allTransactions = readAllTransactionsFromFile();
        Map<String, Transaction> latestSnapshots = new HashMap<>();
        for (Transaction txn : allTransactions) {
            latestSnapshots.put(txn.getTransactionId(), txn);
        }
        return latestSnapshots;
    }

    @Override
    public List<Transaction> readAllLogs() {
        lock.readLock().lock();
        try {
            Map<String, Transaction> latestTransactions = getLatestTransactionSnapshots();
            return new ArrayList<>(latestTransactions.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void deleteLog(String transactionId) {
        lock.writeLock().lock();
        try {
            List<Transaction> allTransactions = readAllTransactionsFromFile();
            List<Transaction> filtered = allTransactions.stream()
                    .filter(txn -> !txn.getTransactionId().equals(transactionId))
                    .toList();

            rewriteLogFile(filtered);

            logger.info("Deleted transaction from WAL: {}", transactionId);
        } catch (IOException e) {
            throw new LogException("Failed to delete from WAL: " + transactionId, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // since log is append only, so we can't delete a line in the middle
    // temp file is created first to prevent the original file from being corrupted in case of crash while rewriting
    private void rewriteLogFile(List<Transaction> filtered) throws IOException {
        Path path = Paths.get(logFilePath);
        Path tempPath = Paths.get(logFilePath + ".tmp");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempPath.toFile()))) {
            for (Transaction transaction : filtered) {
                String jsonLine = objectMapper.writeValueAsString(transaction);
                writer.write(jsonLine);
                writer.newLine();
            }
            writer.flush();
        }

        // atomic replace
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public String getLogFilePath() {
        return logFilePath;
    }
}
