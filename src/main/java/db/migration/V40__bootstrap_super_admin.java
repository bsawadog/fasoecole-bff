package db.migration;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Creates the first platform administrator without a usable, shared initial password. */
public class V40__bootstrap_super_admin extends BaseJavaMigration {
    private static final String EMAIL = "boubacar.sawadogo02@gmail.com";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Long existingId = findExistingUser(connection);
        if (existingId != null) {
            // Do not overwrite credentials, reactivate disabled accounts or promote a claimed email.
            if (!isPlatformAdministrator(connection, existingId)) {
                throw new SQLException("Initialisation SUPER_ADMIN refusée : le courriel configuré appartient déjà "
                        + "à un compte sans rôle SUPER_ADMIN. Vérifiez ce compte avant une attribution manuelle.");
            }
            return;
        }

        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String unusableInitialHash = new BCryptPasswordEncoder().encode(
                Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        Long userId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (first_name, last_name, email, phone, password_hash,
                    active, approved, email_verified, password_set, must_change_password)
                VALUES (?, ?, ?, ?, ?, TRUE, TRUE, FALSE, FALSE, TRUE)
                RETURNING id
                """)) {
            statement.setString(1, "Boubacar");
            statement.setString(2, "Sawadogo");
            statement.setString(3, EMAIL);
            statement.setString(4, "+14389217823");
            statement.setString(5, unusableInitialHash);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Le compte SUPER_ADMIN n’a pas pu être créé");
                userId = result.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO user_platform_roles (user_id, role) VALUES (?, 'SUPER_ADMIN')")) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private Long findExistingUser(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM users WHERE LOWER(email) = LOWER(?) FOR UPDATE")) {
            statement.setString(1, EMAIL);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Long id = result.getLong(1);
                if (result.next()) throw new SQLException("Plusieurs comptes correspondent au courriel du SUPER_ADMIN");
                return id;
            }
        }
    }

    private boolean isPlatformAdministrator(Connection connection, Long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM user_platform_roles WHERE user_id = ? AND role = 'SUPER_ADMIN'")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }
}
