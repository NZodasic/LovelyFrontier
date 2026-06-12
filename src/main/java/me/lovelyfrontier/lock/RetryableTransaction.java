package me.lovelyfrontier.lock;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

public class RetryableTransaction {

    /**
     * Executes a database action in a transaction. If a deadlock (MySQL error 1213) is detected,
     * it will roll back and retry up to 3 times with a 100ms delay.
     * Non-deadlock exceptions are rolled back and thrown immediately.
     */
    public static <T> T execute(Connection conn, Callable<T> action) throws SQLException {
        int retries = 0;
        while (true) {
            try {
                conn.setAutoCommit(false);
                T result = action.call();
                conn.commit();
                return result;
            } catch (SQLException e) {
                // 1213 is MySQL deadlock error code. "40001" is the SQLState for deadlock.
                boolean isDeadlock = (e.getErrorCode() == 1213) || "40001".equals(e.getSQLState());
                if (isDeadlock && retries < 3) {
                    retries++;
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        // Suppress rollback exception to throw original deadlock
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Transaction retry interrupted", ie);
                    }
                } else {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        // Suppress rollback exception to throw original cause
                    }
                    throw e;
                }
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new SQLException("Transaction execution failed", e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Runnable version of transaction execution.
     */
    public static void executeRunnable(Connection conn, Runnable action) throws SQLException {
        execute(conn, () -> {
            action.run();
            return null;
        });
    }
}
