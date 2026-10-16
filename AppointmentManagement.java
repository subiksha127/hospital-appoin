package com.medibook;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;

public class AppointmentManagement {

    public void show(Stage parentStage) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("MediBook - Manage Appointments");

        Label title = new Label("Manage Appointments");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        TableView<AppointmentRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TextField searchField = new TextField();
        searchField.setPromptText("Search patient or doctor name");
        Button searchButton = new Button("Search");
        Button clearButton = new Button("Clear");
        ComboBox<String> filterStatus = new ComboBox<>();
        filterStatus.getItems().addAll("BOOKED", "COMPLETED", "CANCELLED");
        filterStatus.setPromptText("Any status");
        HBox searchBar = new HBox(10, searchField, searchButton, filterStatus, clearButton);

        TableColumn<AppointmentRow, Number> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(cell -> cell.getValue().appointmentIdProperty());

        TableColumn<AppointmentRow, String> patientColumn = new TableColumn<>("Patient");
        patientColumn.setCellValueFactory(cell -> cell.getValue().patientNameProperty());

        TableColumn<AppointmentRow, String> doctorColumn = new TableColumn<>("Doctor");
        doctorColumn.setCellValueFactory(cell -> cell.getValue().doctorNameProperty());

        TableColumn<AppointmentRow, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(cell -> cell.getValue().appointmentDateProperty());

        TableColumn<AppointmentRow, String> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(cell -> cell.getValue().appointmentTimeProperty());

        TableColumn<AppointmentRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cell -> cell.getValue().statusProperty());

        table.getColumns().addAll(idColumn, patientColumn, doctorColumn, dateColumn, timeColumn, statusColumn);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("BOOKED", "COMPLETED", "CANCELLED");
        statusBox.setPromptText("Set status");
        DatePicker newDate = new DatePicker();
        newDate.setPromptText("New date");
        ComboBox<String> newTime = new ComboBox<>();
        for (int hour = 8; hour <= 18; hour++) {
            newTime.getItems().add(String.format("%02d:00:00", hour));
            if (hour < 18) {
                newTime.getItems().add(String.format("%02d:30:00", hour));
            }
        }
        newTime.setPromptText("New time");

        Button updateButton = new Button("Update Status");
        Button changeSlotButton = new Button("Change Date/Time");
        Button refreshButton = new Button("Refresh");
        Button backButton = new Button("Back");

        updateButton.setOnAction(e -> {
            AppointmentRow selected = table.getSelectionModel().getSelectedItem();
            String statusValue = statusBox.getValue();

            if (selected == null) {
                showAlert("Select an appointment first.");
                return;
            }

            if (statusValue == null || statusValue.isEmpty()) {
                showAlert("Choose a new status.");
                return;
            }

            try {
                updateStatus(selected.getAppointmentId(), statusValue);
                loadAppointments(table, searchField.getText(), filterStatus.getValue());
                statusBox.setValue(null);
                showInfo("Appointment status updated.");
            } catch (SQLException ex) {
                showAlert("Unable to update appointment: " + ex.getMessage());
            }
        });

        changeSlotButton.setOnAction(e -> {
            AppointmentRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || newDate.getValue() == null || newTime.getValue() == null) {
                showAlert("Select an appointment and the new date and time.");
                return;
            }
            if (newDate.getValue().isBefore(LocalDate.now())) {
                showAlert("Appointment date cannot be in the past.");
                return;
            }
            if (newDate.getValue().equals(LocalDate.now())
                    && Time.valueOf(newTime.getValue()).toLocalTime().isBefore(java.time.LocalTime.now())) {
                showAlert("Choose an appointment time later than the current time.");
                return;
            }
            try {
                updateSlot(selected.getAppointmentId(), newDate.getValue(), newTime.getValue());
                loadAppointments(table, searchField.getText(), filterStatus.getValue());
                showInfo("Appointment date and time updated.");
            } catch (SQLException ex) {
                showAlert("Unable to change appointment slot: " + ex.getMessage());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, row) -> {
            if (row != null) {
                newDate.setValue(LocalDate.parse(row.getAppointmentDate()));
                newTime.setValue(row.getAppointmentTime().substring(0, 8));
            }
        });
        searchButton.setOnAction(e -> loadAppointments(table, searchField.getText(), filterStatus.getValue()));
        searchField.setOnAction(e -> loadAppointments(table, searchField.getText(), filterStatus.getValue()));
        filterStatus.setOnAction(e -> loadAppointments(table, searchField.getText(), filterStatus.getValue()));
        clearButton.setOnAction(e -> {
            searchField.clear();
            filterStatus.setValue(null);
            loadAppointments(table, "", null);
        });
        refreshButton.setOnAction(e -> loadAppointments(table, searchField.getText(), filterStatus.getValue()));
        backButton.setOnAction(e -> stage.close());

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(statusBox, updateButton, newDate, newTime, changeSlotButton, refreshButton, backButton);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.getChildren().addAll(title, searchBar, table, actions);

        Scene scene = new Scene(root, 980, 520);
        stage.setScene(scene);
        stage.show();

        loadAppointments(table, "", null);
    }

    private void loadAppointments(TableView<AppointmentRow> table, String filter, String statusFilter) {
        String sql = "SELECT a.appointment_id, p.name AS patient_name, d.name AS doctor_name, a.appointment_date, a.appointment_time, a.status " +
                "FROM appointments a JOIN patients p ON p.patient_id = a.patient_id JOIN doctors d ON d.doctor_id = a.doctor_id " +
                ((filter == null || filter.isBlank()) && (statusFilter == null || statusFilter.isBlank())
                        ? "" : "WHERE ")
                + (filter == null || filter.isBlank() ? "" : "(p.name LIKE ? OR d.name LIKE ?) ")
                + (statusFilter == null || statusFilter.isBlank() ? "" :
                        (filter == null || filter.isBlank() ? "" : "AND ") + "a.status = ? ")
                + "ORDER BY a.appointment_date DESC, a.appointment_time DESC";

        ObservableList<AppointmentRow> rows = FXCollections.observableArrayList();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (filter != null && !filter.isBlank()) {
                preparedStatement.setString(1, "%" + filter.trim() + "%");
                preparedStatement.setString(2, "%" + filter.trim() + "%");
            }
            if (statusFilter != null && !statusFilter.isBlank()) {
                preparedStatement.setString(filter == null || filter.isBlank() ? 1 : 3, statusFilter);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                while (resultSet.next()) {
                    rows.add(new AppointmentRow(
                            resultSet.getInt("appointment_id"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("doctor_name"),
                            resultSet.getString("appointment_date"),
                            resultSet.getString("appointment_time"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Unable to load appointments.");
        }
        table.setItems(rows);
    }

    private void updateSlot(int appointmentId, LocalDate date, String time) throws SQLException {
        String doctorSql = "SELECT doctor_id FROM appointments WHERE appointment_id = ? AND status = 'BOOKED'";
        String lockDoctorSql = "SELECT doctor_id FROM doctors WHERE doctor_id = ? FOR UPDATE";
        String conflictSql = """
                SELECT COUNT(*) FROM appointments
                WHERE doctor_id = ? AND appointment_date = ? AND status = 'BOOKED'
                  AND appointment_id <> ?
                  AND TIME_TO_SEC(appointment_time) < TIME_TO_SEC(?) + 1800
                  AND TIME_TO_SEC(appointment_time) + 1800 > TIME_TO_SEC(?)
                """;
        String updateSql = "UPDATE appointments SET appointment_date = ?, appointment_time = ? WHERE appointment_id = ? AND status = 'BOOKED'";
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int doctorId;
                try (PreparedStatement statement = connection.prepareStatement(doctorSql)) {
                    statement.setInt(1, appointmentId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Appointment is no longer booked.");
                        }
                        doctorId = result.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(lockDoctorSql)) {
                    statement.setInt(1, doctorId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("The assigned doctor no longer exists.");
                        }
                    }
                }
            Time slot = Time.valueOf(time);
                try (PreparedStatement statement = connection.prepareStatement(conflictSql)) {
                    statement.setInt(1, doctorId);
                    statement.setDate(2, java.sql.Date.valueOf(date));
                    statement.setInt(3, appointmentId);
                    statement.setTime(4, slot);
                    statement.setTime(5, slot);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && result.getInt(1) > 0) {
                            throw new SQLException("That doctor already has an overlapping 30-minute appointment.");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setDate(1, java.sql.Date.valueOf(date));
                    statement.setTime(2, slot);
                    statement.setInt(3, appointmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Appointment could not be updated.");
                    }
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

    private void updateStatus(int appointmentId, String status) throws SQLException {
        String appointmentSql = "SELECT doctor_id, appointment_date, appointment_time FROM appointments WHERE appointment_id = ? FOR UPDATE";
        String doctorSql = "SELECT doctor_id FROM doctors WHERE doctor_id = ? FOR UPDATE";
        String conflictSql = """
                SELECT COUNT(*) FROM appointments
                WHERE doctor_id = ? AND appointment_date = ? AND status = 'BOOKED'
                  AND appointment_id <> ?
                  AND TIME_TO_SEC(appointment_time) < TIME_TO_SEC(?) + 1800
                  AND TIME_TO_SEC(appointment_time) + 1800 > TIME_TO_SEC(?)
                """;
        String updateSql = "UPDATE appointments SET status = ? WHERE appointment_id = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int doctorId;
                java.sql.Date date;
                Time time;
                try (PreparedStatement statement = connection.prepareStatement(appointmentSql)) {
                    statement.setInt(1, appointmentId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Appointment record no longer exists.");
                        }
                        doctorId = result.getInt("doctor_id");
                        date = result.getDate("appointment_date");
                        time = result.getTime("appointment_time");
                    }
                }
                if ("BOOKED".equals(status)) {
                    if (date == null || time == null) {
                        throw new SQLException("A booked appointment must have a date and time.");
                    }
                    LocalDate appointmentDate = date.toLocalDate();
                    if (appointmentDate.isBefore(LocalDate.now())
                            || (appointmentDate.equals(LocalDate.now())
                            && time.toLocalTime().isBefore(java.time.LocalTime.now()))) {
                        throw new SQLException("A past appointment cannot be marked as booked.");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(doctorSql)) {
                        statement.setInt(1, doctorId);
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                throw new SQLException("Assigned doctor no longer exists.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(conflictSql)) {
                        statement.setInt(1, doctorId);
                        statement.setDate(2, date);
                        statement.setInt(3, appointmentId);
                        statement.setTime(4, time);
                        statement.setTime(5, time);
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next() && result.getInt(1) > 0) {
                                throw new SQLException("Cannot book: this doctor has an overlapping 30-minute appointment.");
                            }
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                    statement.setString(1, status);
                    statement.setInt(2, appointmentId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Appointment status could not be updated.");
                    }
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

    public static class AppointmentRow {
        private final javafx.beans.property.IntegerProperty appointmentId = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.StringProperty patientName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty doctorName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty appointmentDate = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty appointmentTime = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty status = new javafx.beans.property.SimpleStringProperty();

        public AppointmentRow(int appointmentId, String patientName, String doctorName, String appointmentDate, String appointmentTime, String status) {
            setAppointmentId(appointmentId);
            setPatientName(patientName);
            setDoctorName(doctorName);
            setAppointmentDate(appointmentDate);
            setAppointmentTime(appointmentTime);
            setStatus(status);
        }

        public int getAppointmentId() {
            return appointmentId.get();
        }

        public javafx.beans.property.IntegerProperty appointmentIdProperty() {
            return appointmentId;
        }

        public void setAppointmentId(int appointmentId) {
            this.appointmentId.set(appointmentId);
        }

        public String getPatientName() {
            return patientName.get();
        }

        public javafx.beans.property.StringProperty patientNameProperty() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName.set(patientName == null ? "" : patientName);
        }

        public String getDoctorName() {
            return doctorName.get();
        }

        public javafx.beans.property.StringProperty doctorNameProperty() {
            return doctorName;
        }

        public void setDoctorName(String doctorName) {
            this.doctorName.set(doctorName == null ? "" : doctorName);
        }

        public String getAppointmentDate() {
            return appointmentDate.get();
        }

        public javafx.beans.property.StringProperty appointmentDateProperty() {
            return appointmentDate;
        }

        public void setAppointmentDate(String appointmentDate) {
            this.appointmentDate.set(appointmentDate == null ? "" : appointmentDate);
        }

        public String getAppointmentTime() {
            return appointmentTime.get();
        }

        public javafx.beans.property.StringProperty appointmentTimeProperty() {
            return appointmentTime;
        }

        public void setAppointmentTime(String appointmentTime) {
            this.appointmentTime.set(appointmentTime == null ? "" : appointmentTime);
        }

        public String getStatus() {
            return status.get();
        }

        public javafx.beans.property.StringProperty statusProperty() {
            return status;
        }

        public void setStatus(String status) {
            this.status.set(status == null ? "" : status);
        }
    }
}
