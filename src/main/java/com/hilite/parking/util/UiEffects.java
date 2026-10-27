package com.hilite.parking.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared UI timing helpers: live clock, fades and a short error shake. */
public final class UiEffects {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private UiEffects() {
    }

    public static void startClock(Label dateLabel, Label timeLabel) {
        updateClock(dateLabel, timeLabel);
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateClock(dateLabel, timeLabel)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private static void updateClock(Label dateLabel, Label timeLabel) {
        LocalDateTime now = LocalDateTime.now();
        dateLabel.setText(DATE.format(now));
        timeLabel.setText(TIME.format(now));
    }

    public static void fadeIn(Node node, Duration duration) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    public static void shake(Node node) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50), new KeyValue(node.translateXProperty(), -7)),
                new KeyFrame(Duration.millis(105), new KeyValue(node.translateXProperty(), 7)),
                new KeyFrame(Duration.millis(160), new KeyValue(node.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(220), new KeyValue(node.translateXProperty(), 0))
        );
        timeline.play();
    }
}
