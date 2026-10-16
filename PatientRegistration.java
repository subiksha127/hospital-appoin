package com.medibook;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.GeneralSecurityException;

public class PatientRegistration {

    public void show(Stage parentStage) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("MediBook - Patient Registration");

        Label title = new Label("Patient Registration");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20));

        Label usernameLabel = new Label("Username:");
        Label passwordLabel = new Label("Password:");
        Label confirmPasswordLabel = new Label("Confirm Password:");
        Label nameLabel = new Label("Full Name:");
        Label ageLabel = new Label("Age:");
        Label genderLabel = new Label("Gender:");
        Label phoneLabel = new Label("Phone:");
        Label emailLabel = new Label("Email:");

        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        TextField nameField = new TextField();
        TextField ageField = new TextField();
        ComboBox<String> genderBox = new ComboBox<>();
        genderBox.getItems().addAll("Male", "Female", "Other");
        TextField phoneField = new TextField();
        TextField emailField = new TextField();

        usernameField.setPromptText("Choose username");
        passwordField.setPromptText("Password");
        confirmPasswordField.setPromptText("Repeat password");
        nameField.setPromptText("Patient name");
        ageField.setPromptText("Age");
        phoneField.setPromptText("Phone number");
        emailField.setPromptText("Email address");

        form.add(usernameLabel, 0, 0);
        form.add(usernameField, 1, 0);
        form.add(passwordLabel, 0, 1);
        form.add(passwordField, 1, 1);
        form.add(confirmPasswordLabel, 0, 2);
        form.add(confirmPasswordField, 1, 2);
        form.add(nameLabel, 0, 3);
        form.add(nameField, 1, 3);
        form.add(ageLabel, 0, 4);
        form.add(ageField, 1, 4);
        form.add(genderLabel, 0, 5);
        form.add(genderBox, 1, 5);
        form.add(phoneLabel, 0, 6);
        form.add(phoneField, 1, 6);
        form.add(emailLabel, 0, 7);
        form.add(emailField, 1, 7);

        Button registerButton = new Button("Register");
        Button cancelButton = new Button("Cancel");

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(cancelButton, registerButton);

        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();
            String name = nameField.getText().trim();
            String ageText = ageField.getText().trim();
            String gender = genderBox.getValue();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || name.isEmpty()
                    || ageText.isEmpty() || gender == null || phone.isEmpty() || email.isEmpty()) {
                showAlert("All fields are required.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showAlert("Passwords do not match.");
                return;
            }

            if (password.length() < 4) {
                showAlert("Password must be at least 4 characters long.");
                return;
            }

            try {
                int age = Integer.parseInt(ageText);
                if (age < 1 || age > 130) {
                    showAlert("Age must be between 1 and 130.");
                    return;
                }
                if (username.length() > 50 || name.length() > 100 || phone.length() > 15 || email.length() > 100) {
                    showAlert("One or more fields exceed the allowed length.");
                    return;
                }
                if (!phone.matches("[+0-9() -]{7,15}")) {
                    showAlert("Enter a valid phone number.");
                    return;
                }
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    showAlert("Enter a valid email address.");
                    return;
                }
                registerPatient(username, password, name, age, gender, phone, email);
                showInfo("Registration successful. You can now login as a patient.");
                stage.close();
            } catch (NumberFormatException ex) {
                showAlert("Age must be a valid number.");
            } catch (SQLException ex) {
                showAlert(ex.getSQLState() != null && ex.getSQLState().startsWith("23")
                        ? "That username is already registered."
                        : "Registration failed: " + ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> stage.close());

        VBox root = new VBox(18);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_CENTER);
        root.getChildren().addAll(title, form, actions);

        Scene scene = new Scene(root, 520, 520);
        stage.setScene(scene);
        stage.show();
    }

    private void registerPatient(String username, String password, String name, int age,
                                String gender, String phone, String email) throws SQLException {
        String userSql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'PATIENT')";
        String patientSql = "INSERT INTO patients (user_id, name, age, gender, phone, email) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement userStatement = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                    userStatement.setString(1, username);
                    try {
                        userStatement.setString(2, PasswordUtil.hash(password));
                    } catch (GeneralSecurityException e) {
                        throw new SQLException("Could not securely process the password.", e);
                    }
                    userStatement.executeUpdate();

                    try (ResultSet generatedKeys = userStatement.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            throw new SQLException("User record was not created.");
                        }

                        int userId = generatedKeys.getInt(1);

                        try (PreparedStatement patientStatement = connection.prepareStatement(patientSql)) {
                            patientStatement.setInt(1, userId);
                            patientStatement.setString(2, name);
                            patientStatement.setInt(3, age);
                            patientStatement.setString(4, gender);
                            patientStatement.setString(5, phone);
                            patientStatement.setString(6, email);
                            patientStatement.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("MediBook");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("MediBook");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
