package com.medibook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class DatabaseConnection {

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3307/hospital_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    public static Connection getConnection() throws SQLException {
        Properties properties = new Properties();
        Path configPath = Path.of(System.getProperty("medibook.config", "database.properties"));
        if (Files.isRegularFile(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException e) {
                throw new SQLException("Could not read MediBook database configuration at " + configPath, e);
            }
        }

        String url = setting("MEDIBOOK_DB_URL", properties, "url", DEFAULT_URL);
        String user = setting("MEDIBOOK_DB_USER", properties, "username", "root");
        String password = setting("MEDIBOOK_DB_PASSWORD", properties, "password", null);
        if (password == null || password.isBlank()) {
            throw new SQLException("Database password is not configured. Set MEDIBOOK_DB_PASSWORD "
                    + "or add password to the ignored database.properties file.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    private static String setting(String environmentName, Properties properties, String key, String fallback) {
        String systemValue = System.getProperty(environmentName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return properties.getProperty(key, fallback);
    }
}