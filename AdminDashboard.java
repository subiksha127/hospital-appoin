package com.medibook;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminDashboard {

    private final Label doctors = metricValue();
    private final Label patients = metricValue();
    private final Label appointments = metricValue();
    private final Label pendingAppointments = metricValue();
    private final Label bills = metricValue();
    private final Label billingTotal = metricValue();
    private final Label error = new Label();

    public void show(Stage stage) {
        Label title = new Label("MediBook | Administration");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #12324a;");
        Label welcome = new Label("Hospital Appointment Management");

        GridPane metrics = new GridPane();
        metrics.setHgap(14);
        metrics.setVgap(14);
        metrics.setAlignment(Pos.CENTER);
        metrics.add(createCard("Doctors", doctors), 0, 0);
        metrics.add(createCard("Patients", patients), 1, 0);
        metrics.add(createCard("Appointments", appointments), 2, 0);
        metrics.add(createCard("Pending appointments", pendingAppointments), 0, 1);
        metrics.add(createCard("Bills", bills), 1, 1);
        metrics.add(createCard("Total billing amount", billingTotal), 2, 1);

        Button doctorButton = new Button("Manage Doctors");
        Button patientButton = new Button("Manage Patients");
        Button appointmentButton = new Button("Manage Appointments");
        Button billButton = new Button("Manage Bills");
        Button refreshButton = new Button("Refresh Statistics");
        Button logoutButton = new Button("Logout");

        HBox navigation = new HBox(10, doctorButton, patientButton, appointmentButton, billButton);
        navigation.setAlignment(Pos.CENTER);
        HBox controls = new HBox(10, refreshButton, logoutButton);
        controls.setAlignment(Pos.CENTER);
        doctorButton.setOnAction(e -> new DoctorManagement().show(stage));
        patientButton.setOnAction(e -> new PatientManagement().show(stage));
        appointmentButton.setOnAction(e -> new AppointmentManagement().show(stage));
        billButton.setOnAction(e -> new BillingManagement().show(stage));
        refreshButton.setOnAction(e -> loadStatistics());
        logoutButton.setOnAction(e -> new App().start(stage));

        stage.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (isFocused) {
                loadStatistics();
            }
        });

        VBox root = new VBox(22, title, welcome, metrics, navigation, controls, error);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f4f8fb;");
        stage.setTitle("MediBook - Admin Dashboard");
        stage.setScene(new Scene(root, 1000, 650));
        stage.show();
        loadStatistics();
    }

    private void loadStatistics() {
        error.setText("");
        doctors.setText("…");
        patients.setText("…");
        appointments.setText("…");
        pendingAppointments.setText("…");
        bills.setText("…");
        billingTotal.setText("…");

        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM doctors) AS doctor_count,
                    (SELECT COUNT(*) FROM patients) AS patient_count,
                    (SELECT COUNT(*) FROM appointments) AS appointment_count,
                    (SELECT COUNT(*) FROM appointments WHERE status = 'BOOKED') AS pending_count,
                    (SELECT COUNT(*) FROM bills) AS bill_count,
                    (SELECT COALESCE(SUM(amount), 0) FROM bills) AS billing_total
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Dashboard statistics query returned no result.");
            }
            doctors.setText(Integer.toString(result.getInt("doctor_count")));
            patients.setText(Integer.toString(result.getInt("patient_count")));
            appointments.setText(Integer.toString(result.getInt("appointment_count")));
            pendingAppointments.setText(Integer.toString(result.getInt("pending_count")));
            bills.setText(Integer.toString(result.getInt("bill_count")));
            billingTotal.setText(result.getBigDecimal("billing_total").toPlainString());
        } catch (SQLException e) {
            doctors.setText("—");
            patients.setText("—");
            appointments.setText("—");
            pendingAppointments.setText("—");
            bills.setText("—");
            billingTotal.setText("—");
            error.setText("Could not load dashboard statistics: " + e.getMessage());
            error.setStyle("-fx-text-fill: #a32020;");
            e.printStackTrace();
        }
    }

    private static Label metricValue() {
        Label value = new Label("…");
        value.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #126782;");
        return value;
    }

    private VBox createCard(String title, Label value) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox card = new VBox(8, heading, value);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(18));
        card.setPrefSize(240, 105);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #d8e5ec; -fx-border-radius: 10;");
        return card;
    }
}
