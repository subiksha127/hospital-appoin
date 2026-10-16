package com.medibook;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class App extends javafx.application.Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("MediBook");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #12324a;");

        Label subtitle = new Label("Hospital Appointment Management System");

        TextField username = new TextField();
        username.setPromptText("Username");
        username.setMaxWidth(300);

        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setMaxWidth(300);

        Button loginButton = new Button("Login");
        loginButton.setMaxWidth(300);

        Button registerButton = new Button("Register Patient");
        registerButton.setMaxWidth(300);

        Label message = new Label();
        message.setWrapText(true);

        loginButton.setOnAction(event -> {
            String loginName = username.getText().trim();
            String loginPassword = password.getText();
            if (loginName.isEmpty() || loginPassword.isEmpty()) {
                message.setText("Please enter username and password.");
                return;
            }

            String role = null;
            String storedPassword = null;
            int userId = -1;
            String sql = "SELECT user_id, role, password FROM users WHERE username = ?";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, loginName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        userId = result.getInt("user_id");
                        role = result.getString("role");
                        storedPassword = result.getString("password");
                    }
                }

                if (storedPassword != null && PasswordUtil.verify(loginPassword, storedPassword)
                        && !storedPassword.startsWith("PBKDF2$")) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE users SET password = ? WHERE user_id = ?")) {
                        update.setString(1, PasswordUtil.hash(loginPassword));
                        update.setInt(2, userId);
                        update.executeUpdate();
                    }
                }
            } catch (SQLException | GeneralSecurityException exception) {
                exception.printStackTrace();
                message.setText("Login failed: " + exception.getMessage());
                return;
            }

            try {
                if (storedPassword == null || !PasswordUtil.verify(loginPassword, storedPassword)) {
                    message.setText("Invalid username or password.");
                } else if ("ADMIN".equalsIgnoreCase(role)) {
                    new AdminDashboard().show(stage);
                } else if ("PATIENT".equalsIgnoreCase(role)) {
                    new PatientDashboard().show(stage, loginName);
                } else {
                    message.setText("This account has an unsupported role.");
                }
            } catch (GeneralSecurityException exception) {
                exception.printStackTrace();
                message.setText("Unable to verify login credentials.");
            }
        });

        registerButton.setOnAction(event -> new PatientRegistration().show(stage));

        VBox root = new VBox(15, title, subtitle, username, password, loginButton, registerButton, message);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f4f8fb;");

        stage.setTitle("MediBook - Login");
        stage.setScene(new Scene(root, 600, 450));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}