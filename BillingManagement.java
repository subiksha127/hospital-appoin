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
import java.math.BigDecimal;

public class BillingManagement {

    public void show(Stage parentStage) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("MediBook - Manage Bills");
        showInternal(stage, "SELECT b.bill_id, p.name AS patient_name, d.name AS doctor_name, b.amount, b.payment_status " +
                "FROM bills b JOIN appointments a ON a.appointment_id = b.appointment_id " +
                "JOIN patients p ON p.patient_id = a.patient_id JOIN doctors d ON d.doctor_id = a.doctor_id " +
                "ORDER BY b.bill_id DESC", null, null);
    }

    public void showForPatient(Stage parentStage, int patientId) {
        Stage stage = new Stage();
        stage.initOwner(parentStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("MediBook - My Bills");
        showInternal(stage, "SELECT b.bill_id, p.name AS patient_name, d.name AS doctor_name, b.amount, b.payment_status " +
                "FROM bills b JOIN appointments a ON a.appointment_id = b.appointment_id " +
                "JOIN patients p ON p.patient_id = a.patient_id JOIN doctors d ON d.doctor_id = a.doctor_id " +
                "WHERE p.patient_id = ? ORDER BY b.bill_id DESC", "patient", patientId);
    }

    private void showInternal(Stage stage, String sql, String mode, Integer patientId) {
        Label title = new Label(mode == null ? "Manage Bills" : "My Bills");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        TableView<BillRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BillRow, Number> billIdColumn = new TableColumn<>("Bill ID");
        billIdColumn.setCellValueFactory(cell -> cell.getValue().billIdProperty());

        TableColumn<BillRow, String> patientColumn = new TableColumn<>("Patient");
        patientColumn.setCellValueFactory(cell -> cell.getValue().patientNameProperty());

        TableColumn<BillRow, String> doctorColumn = new TableColumn<>("Doctor");
        doctorColumn.setCellValueFactory(cell -> cell.getValue().doctorNameProperty());

        TableColumn<BillRow, String> amountColumn = new TableColumn<>("Amount");
        amountColumn.setCellValueFactory(cell -> cell.getValue().amountProperty());

        TableColumn<BillRow, String> statusColumn = new TableColumn<>("Payment Status");
        statusColumn.setCellValueFactory(cell -> cell.getValue().paymentStatusProperty());

        table.getColumns().addAll(billIdColumn, patientColumn, doctorColumn, amountColumn, statusColumn);
        TextField searchField = new TextField();
        searchField.setPromptText("Search bills by patient or bill ID");
        Button searchButton = new Button("Search");
        Button clearSearchButton = new Button("Clear");
        HBox searchBar = new HBox(10, searchField, searchButton, clearSearchButton);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("PENDING", "PAID", "OVERDUE");
        statusBox.setPromptText("Update status");

        Button updateButton = new Button("Update");
        Button createButton = new Button("Create Bill");
        Button refreshButton = new Button("Refresh");
        Button backButton = new Button("Back");

        updateButton.setOnAction(e -> {
            BillRow selected = table.getSelectionModel().getSelectedItem();
            String statusValue = statusBox.getValue();

            if (selected == null) {
                showAlert("Select a bill first.");
                return;
            }

            if (statusValue == null || statusValue.isEmpty()) {
                showAlert("Choose a payment status.");
                return;
            }

            try {
                updateStatus(selected.getBillId(), statusValue);
                loadBills(table, sql, mode, patientId, searchField.getText());
                statusBox.setValue(null);
                showInfo("Bill payment status updated.");
            } catch (SQLException ex) {
                showAlert("Unable to update bill: " + ex.getMessage());
            }
        });

        refreshButton.setOnAction(e -> loadBills(table, sql, mode, patientId, searchField.getText()));
        searchButton.setOnAction(e -> loadBills(table, sql, mode, patientId, searchField.getText()));
        searchField.setOnAction(e -> loadBills(table, sql, mode, patientId, searchField.getText()));
        clearSearchButton.setOnAction(e -> {
            searchField.clear();
            loadBills(table, sql, mode, patientId, "");
        });
        backButton.setOnAction(e -> stage.close());
        boolean admin = mode == null;
        updateButton.setVisible(admin);
        updateButton.setManaged(admin);
        statusBox.setVisible(admin);
        statusBox.setManaged(admin);
        createButton.setVisible(admin);
        createButton.setManaged(admin);
        createButton.setOnAction(e -> createBill(table, sql, mode, patientId));

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(statusBox, updateButton, createButton, refreshButton, backButton);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.getChildren().addAll(title, searchBar, table, actions);

        Scene scene = new Scene(root, 820, 500);
        stage.setScene(scene);
        stage.show();

        loadBills(table, sql, mode, patientId, "");
    }

    private void createBill(TableView<BillRow> table, String listSql, String mode, Integer patientId) {
        ObservableList<BillAppointment> available = FXCollections.observableArrayList();
        String sql = """
                SELECT a.appointment_id, p.name AS patient_name, d.name AS doctor_name,
                       COALESCE(d.fee, 0) AS fee
                FROM appointments a
                JOIN patients p ON p.patient_id = a.patient_id
                JOIN doctors d ON d.doctor_id = a.doctor_id
                LEFT JOIN bills b ON b.appointment_id = a.appointment_id
                WHERE b.bill_id IS NULL AND a.status <> 'CANCELLED'
                ORDER BY a.appointment_date DESC, a.appointment_time DESC
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                available.add(new BillAppointment(result.getInt("appointment_id"),
                        result.getString("patient_name"), result.getString("doctor_name"),
                        result.getBigDecimal("fee")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Unable to load billable appointments: " + e.getMessage());
            return;
        }
        if (available.isEmpty()) {
            showAlert("There are no unbilled appointments.");
            return;
        }

        ComboBox<BillAppointment> appointmentBox = new ComboBox<>(available);
        appointmentBox.setMaxWidth(Double.MAX_VALUE);
        TextField amountField = new TextField();
        appointmentBox.setOnAction(e -> {
            BillAppointment selected = appointmentBox.getValue();
            if (selected != null) {
                amountField.setText(selected.fee().toPlainString());
            }
        });
        appointmentBox.getSelectionModel().selectFirst();
        amountField.setText(available.get(0).fee().toPlainString());

        javafx.scene.layout.GridPane form = new javafx.scene.layout.GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new javafx.scene.control.Label("Appointment"), appointmentBox);
        form.addRow(1, new javafx.scene.control.Label("Total charge"), amountField);

        ButtonType save = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(table.getScene().getWindow());
        dialog.setTitle("Create Bill");
        dialog.setHeaderText("Set the consultation/service charge");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) {
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            if (amount.signum() < 0 || amount.scale() > 2) {
                showAlert("Enter a non-negative amount with up to two decimal places.");
                return;
            }
            BillAppointment selected = appointmentBox.getValue();
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    String appointmentSql = "SELECT appointment_id, status FROM appointments WHERE appointment_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(appointmentSql)) {
                        statement.setInt(1, selected.appointmentId());
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next() || "CANCELLED".equalsIgnoreCase(result.getString("status"))) {
                                throw new SQLException("Appointment is missing or cancelled.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT bill_id FROM bills WHERE appointment_id = ?")) {
                        statement.setInt(1, selected.appointmentId());
                        try (ResultSet result = statement.executeQuery()) {
                            if (result.next()) {
                                throw new SQLException("This appointment already has a bill.");
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO bills (appointment_id, amount, payment_status) VALUES (?, ?, 'PENDING')")) {
                        statement.setInt(1, selected.appointmentId());
                        statement.setBigDecimal(2, amount);
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
            loadBills(table, listSql, mode, patientId, "");
            showInfo("Bill created successfully.");
        } catch (NumberFormatException e) {
            showAlert("Enter a valid charge amount.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Unable to create bill: " + e.getMessage());
        }
    }

    private void loadBills(TableView<BillRow> table, String sql, String mode, Integer patientId, String filter) {
        ObservableList<BillRow> rows = FXCollections.observableArrayList();
        String baseSql = sql.replace(" ORDER BY b.bill_id DESC", "");
        String query = baseSql;
        if (filter != null && !filter.isBlank()) {
            query += ("patient".equals(mode) ? " AND " : " WHERE ")
                    + "(p.name LIKE ? OR CAST(b.bill_id AS CHAR) LIKE ?)";
        }
        query += " ORDER BY b.bill_id DESC";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            if ("patient".equals(mode) && patientId != null) {
                preparedStatement.setInt(1, patientId);
            }
            if (filter != null && !filter.isBlank()) {
                int parameter = "patient".equals(mode) ? 2 : 1;
                preparedStatement.setString(parameter, "%" + filter.trim() + "%");
                preparedStatement.setString(parameter + 1, "%" + filter.trim() + "%");
            }

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new BillRow(
                            resultSet.getInt("bill_id"),
                            resultSet.getString("patient_name"),
                            resultSet.getString("doctor_name"),
                            resultSet.getString("amount"),
                            resultSet.getString("payment_status")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Unable to load bills: " + e.getMessage());
        }

        table.setItems(rows);
    }

    private void updateStatus(int billId, String status) throws SQLException {
        String sql = "UPDATE bills SET payment_status = ? WHERE bill_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, status);
            preparedStatement.setInt(2, billId);
            preparedStatement.executeUpdate();
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

    private record BillAppointment(int appointmentId, String patient, String doctor, BigDecimal fee) {
        @Override
        public String toString() {
            return "#" + appointmentId + " - " + patient + " / " + doctor + " ($" + fee + ")";
        }
    }

    public static class BillRow {
        private final javafx.beans.property.IntegerProperty billId = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.StringProperty patientName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty doctorName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty amount = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty paymentStatus = new javafx.beans.property.SimpleStringProperty();

        public BillRow(int billId, String patientName, String doctorName, String amount, String paymentStatus) {
            setBillId(billId);
            setPatientName(patientName);
            setDoctorName(doctorName);
            setAmount(amount);
            setPaymentStatus(paymentStatus);
        }

        public int getBillId() {
            return billId.get();
        }

        public javafx.beans.property.IntegerProperty billIdProperty() {
            return billId;
        }

        public void setBillId(int billId) {
            this.billId.set(billId);
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

        public String getAmount() {
            return amount.get();
        }

        public javafx.beans.property.StringProperty amountProperty() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount.set(amount == null ? "" : amount);
        }

        public String getPaymentStatus() {
            return paymentStatus.get();
        }

        public javafx.beans.property.StringProperty paymentStatusProperty() {
            return paymentStatus;
        }

        public void setPaymentStatus(String paymentStatus) {
            this.paymentStatus.set(paymentStatus == null ? "" : paymentStatus);
        }
    }
}
