package com.medibook;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentBooking {

    public void show(Stage parentStage, int patientId) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("MediBook - Book Appointment");

        Label title = new Label("Book Appointment");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        Label doctorLabel = new Label("Doctor:");
        Label dateLabel = new Label("Date:");
        Label timeLabel = new Label("Time:");

        ComboBox<DoctorOption> doctorBox = new ComboBox<>();
        doctorBox.setPrefWidth(300);
        DatePicker datePicker = new DatePicker();
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        ComboBox<String> timeBox = new ComboBox<>();
        timeBox.getItems().addAll(
                "09:00:00", "09:30:00", "10:00:00", "10:30:00",
                "11:00:00", "11:30:00", "12:00:00", "13:00:00",
                "14:00:00", "14:30:00", "15:00:00", "15:30:00",
                "16:00:00", "16:30:00", "17:00:00"
        );
        timeBox.setPrefWidth(300);

        loadDoctors(doctorBox);

        GridPane form = new GridPane();
        form.setPadding(new Insets(20));
        form.setHgap(12);
        form.setVgap(10);
        form.add(doctorLabel, 0, 0);
        form.add(doctorBox, 1, 0);
        form.add(dateLabel, 0, 1);
        form.add(datePicker, 1, 1);
        form.add(timeLabel, 0, 2);
        form.add(timeBox, 1, 2);

        Button bookButton = new Button("Book");
        Button cancelButton = new Button("Cancel");

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(cancelButton, bookButton);

        bookButton.setOnAction(e -> {
            DoctorOption selectedDoctor = doctorBox.getValue();
            LocalDate appointmentDate = datePicker.getValue();
            String time = timeBox.getValue();

            if (selectedDoctor == null || appointmentDate == null || time == null || time.isEmpty()) {
                showAlert("Please select a doctor, date, and time.");
                return;
            }
            if (patientId <= 0) {
                showAlert("Could not identify the signed-in patient. Please log out and sign in again.");
                return;
            }
            if (appointmentDate.isBefore(LocalDate.now())) {
                showAlert("Appointments cannot be booked in the past.");
                return;
            }
            if (appointmentDate.equals(LocalDate.now()) && LocalTime.parse(time).isBefore(LocalTime.now())) {
                showAlert("Choose a time later than the current time.");
                return;
            }

            try {
                saveAppointment(patientId, selectedDoctor.getDoctorId(), appointmentDate, time);
                showInfo("Appointment booked successfully.");
                stage.close();
            } catch (SQLException ex) {
                showAlert("Failed to book appointment: " + ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> stage.close());

        VBox root = new VBox(18);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(25));
        root.getChildren().addAll(title, form, actions);

        Scene scene = new Scene(root, 500, 280);
        stage.setScene(scene);
        stage.show();
    }

    private void saveAppointment(int patientId, int doctorId, LocalDate date, String time) throws SQLException {
        String doctorLockSql = "SELECT doctor_id FROM doctors WHERE doctor_id = ? FOR UPDATE";
        String patientLockSql = "SELECT patient_id FROM patients WHERE patient_id = ? FOR UPDATE";
        String conflictSql = """
                SELECT COUNT(*) FROM appointments
                WHERE doctor_id = ? AND appointment_date = ? AND status = 'BOOKED'
                  AND TIME_TO_SEC(appointment_time) < TIME_TO_SEC(?) + 1800
                  AND TIME_TO_SEC(appointment_time) + 1800 > TIME_TO_SEC(?)
                """;
        String insertSql = "INSERT INTO appointments (patient_id, doctor_id, appointment_date, appointment_time, status) VALUES (?, ?, ?, ?, 'BOOKED')";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(patientLockSql)) {
                    lock.setInt(1, patientId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Signed-in patient record no longer exists.");
                        }
                    }
                }
                try (PreparedStatement lock = connection.prepareStatement(doctorLockSql)) {
                    lock.setInt(1, doctorId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Selected doctor no longer exists.");
                        }
                    }
                }
                try (PreparedStatement conflict = connection.prepareStatement(conflictSql)) {
                    conflict.setInt(1, doctorId);
                    conflict.setDate(2, java.sql.Date.valueOf(date));
                    conflict.setTime(3, java.sql.Time.valueOf(time));
                    conflict.setTime(4, java.sql.Time.valueOf(time));
                    try (ResultSet result = conflict.executeQuery()) {
                        if (result.next() && result.getInt(1) > 0) {
                            throw new SQLException("This doctor already has an appointment within this 30-minute slot.");
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setInt(1, patientId);
                    insert.setInt(2, doctorId);
                    insert.setDate(3, java.sql.Date.valueOf(date));
                    insert.setTime(4, java.sql.Time.valueOf(time));
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void loadDoctors(ComboBox<DoctorOption> doctorBox) {
        ObservableList<DoctorOption> doctors = FXCollections.observableArrayList();
        String sql = "SELECT doctor_id, name, specialization, fee FROM doctors ORDER BY name";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                int doctorId = resultSet.getInt("doctor_id");
                String name = resultSet.getString("name");
                String specialization = resultSet.getString("specialization");
                String fee = resultSet.getString("fee");
                doctors.add(new DoctorOption(doctorId, name, specialization, fee));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Unable to load doctors: " + e.getMessage());
        }

        doctorBox.setItems(doctors);
        doctorBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DoctorOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " - " + item.getSpecialization());
            }
        });
        doctorBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DoctorOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " - " + item.getSpecialization());
            }
        });
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

    public static class DoctorOption {
        private final int doctorId;
        private final String name;
        private final String specialization;
        private final String fee;

        public DoctorOption(int doctorId, String name, String specialization, String fee) {
            this.doctorId = doctorId;
            this.name = name;
            this.specialization = specialization;
            this.fee = fee;
        }

        public int getDoctorId() {
            return doctorId;
        }

        public String getName() {
            return name;
        }

        public String getSpecialization() {
            return specialization;
        }

        public String getFee() {
            return fee;
        }
    }
}
