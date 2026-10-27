package com.hilite.parking.controller;

import com.hilite.parking.Navigator;
import com.hilite.parking.util.Icons;
import com.hilite.parking.util.UiEffects;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.net.URL;
import java.util.ResourceBundle;

/** Shared header for the customer kiosk screens. */
public class KioskHeaderController implements Initializable, NavigatorAware {

    @FXML private Label dateLabel;
    @FXML private Label timeLabel;
    @FXML private Label titleLabel;
    @FXML private Button adminButton;

    private Navigator navigator;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        UiEffects.startClock(dateLabel, timeLabel);
        adminButton.setGraphic(Icons.lock(17, "icon-light"));
        adminButton.setTooltip(new Tooltip("Administrator access (Ctrl+Shift+A)"));
        adminButton.setOnAction(event -> {
            if (navigator != null) {
                navigator.showAdminPin();
            }
        });
    }

    @Override
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    public void configure(String title) {
        boolean hasTitle = title != null && !title.isBlank();
        titleLabel.setText(hasTitle ? title : "");
        titleLabel.setVisible(hasTitle);
        titleLabel.setManaged(hasTitle);
    }
}
