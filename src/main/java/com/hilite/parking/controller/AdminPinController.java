package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.preview.PreviewData;
import com.hilite.parking.util.GarageBackdrop;
import com.hilite.parking.util.Icons;
import com.hilite.parking.util.UiEffects;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

/** Demonstration PIN gate. The preview PIN is never shown in the UI. */
public class AdminPinController implements Initializable, NavigatorAware {

    @FXML private StackPane backdropHost;
    @FXML private Label dateLabel;
    @FXML private Label timeLabel;
    @FXML private Button backButton;
    @FXML private VBox pinCard;
    @FXML private StackPane shieldBadge;
    @FXML private PasswordField pinField;
    @FXML private Label errorLabel;
    @FXML private Button backspaceButton;
    @FXML private Button unlockButton;

    private Navigator navigator;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        backdropHost.getChildren().add(new GarageBackdrop());
        UiEffects.startClock(dateLabel, timeLabel);
        shieldBadge.getChildren().add(Icons.shield(30, "icon-gold"));
        backButton.setGraphic(Icons.logout(16, "icon-light"));
        backspaceButton.setGraphic(Icons.backspace(19, "icon-light"));
        unlockButton.setGraphic(Icons.lock(17, "icon-ink"));
        pinField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,4}") ? change : null));
        pinField.textProperty().addListener((observable, oldValue, newValue) -> hideError());
        Platform.runLater(pinField::requestFocus);
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    private void handleDigit(ActionEvent event) {
        if (!(event.getSource() instanceof Button button)) {
            return;
        }
        String digit = button.getText();
        if (digit == null || !digit.matches("\\d") || pinField.getText().length() >= 4) {
            return;
        }
        hideError();
        pinField.appendText(digit);
        pinField.requestFocus();
        pinField.positionCaret(pinField.getText().length());
    }

    @FXML
    private void handleClear() {
        pinField.clear();
        hideError();
        pinField.requestFocus();
    }

    @FXML
    private void handleBackspace() {
        String current = pinField.getText();
        if (!current.isEmpty()) {
            pinField.setText(current.substring(0, current.length() - 1));
            pinField.positionCaret(pinField.getText().length());
        }
        hideError();
        pinField.requestFocus();
    }

    @FXML
    private void handleUnlock() {
        if (PreviewData.ADMIN_PIN.equals(pinField.getText())) {
            pinField.clear();
            if (navigator != null) {
                navigator.showDashboard();
            }
            return;
        }
        pinField.clear();
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        UiEffects.shake(pinCard);
        pinField.requestFocus();
    }

    @FXML
    private void handleBack() {
        if (navigator != null) {
            navigator.showKiosk();
        }
    }

    @FXML
    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            handleBack();
            event.consume();
        }
    }

    private void hideError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }
}
