package com.example.shortener.persistence;

import com.example.shortener.domain.CodeCollisionException;
import com.example.shortener.domain.ShortLink;
import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcLinkRepository implements LinkRepository {

    private static final String INSERT =
            "INSERT INTO short_links (code, destination) VALUES (?, ?)";
    private static final String FIND =
            "SELECT code, destination, redirect_count FROM short_links WHERE code = ?";
    private static final String INCREMENT =
            "UPDATE short_links SET redirect_count = redirect_count + 1 WHERE code = ?";
    private static final String DESTINATION =
            "SELECT destination FROM short_links WHERE code = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate autocommit;
    private final TransactionTemplate redirect;

    public JdbcLinkRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.autocommit = new TransactionTemplate(transactionManager);
        // Suspend any caller transaction so a collision cannot poison a later attempt.
        this.autocommit.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.redirect = new TransactionTemplate(transactionManager);
        // Return only after this increment commits, even if the caller has a transaction.
        this.redirect.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void insert(String code, String destination) {
        try {
            autocommit.executeWithoutResult(status -> jdbc.execute((ConnectionCallback<Void>) connection -> {
                boolean originalAutoCommit = connection.getAutoCommit();
                if (!originalAutoCommit) {
                    connection.setAutoCommit(true);
                }
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    statement.setString(1, code);
                    statement.setString(2, destination);
                    statement.executeUpdate();
                    return null;
                } finally {
                    if (!originalAutoCommit) {
                        connection.setAutoCommit(false);
                    }
                }
            }));
        } catch (DuplicateKeyException collision) {
            // The required schema has one unique constraint: the code primary key.
            throw new CodeCollisionException(collision);
        }
    }

    @Override
    public Optional<ShortLink> find(String code) {
        return jdbc.query(FIND, (row, index) -> new ShortLink(
                row.getString("code"), row.getString("destination"),
                row.getLong("redirect_count")), code).stream().findFirst();
    }

    @Override
    public Optional<String> incrementAndGetDestination(String code) {
        return Objects.requireNonNull(redirect.execute(status -> {
            if (jdbc.update(INCREMENT, code) == 0) {
                return Optional.<String>empty();
            }
            // The UPDATE holds the row lock through this read and the final commit.
            return Optional.of(Objects.requireNonNull(
                    jdbc.queryForObject(DESTINATION, String.class, code)));
        }));
    }

    @Override
    public void checkConnectivity() {
        jdbc.queryForObject("SELECT 1", Integer.class);
    }
}
