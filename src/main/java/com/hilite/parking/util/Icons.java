package com.hilite.parking.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/**
 * Small hand-built vector icon set. Using JavaFX shapes avoids remote images and icon fonts.
 * Icons are decorative; callers must still provide text labels.
 */
public final class Icons {

    public static final String CAR_PATH =
            "M4.6 11.2 L6.2 6.9 C6.6 5.8 7.6 5.1 8.8 5.1 L15.2 5.1 C16.4 5.1 17.4 5.8 17.8 6.9 "
                    + "L19.4 11.2 L20.4 11.6 C21.1 11.9 21.5 12.5 21.5 13.2 L21.5 16.4 "
                    + "C21.5 17 21.1 17.4 20.5 17.4 L19.4 17.4 L19.4 18.3 C19.4 18.8 19 19.2 18.5 19.2 "
                    + "L17.6 19.2 C17.1 19.2 16.7 18.8 16.7 18.3 L16.7 17.4 L7.3 17.4 L7.3 18.3 "
                    + "C7.3 18.8 6.9 19.2 6.4 19.2 L5.5 19.2 C5 19.2 4.6 18.8 4.6 18.3 L4.6 17.4 "
                    + "L3.5 17.4 C2.9 17.4 2.5 17 2.5 16.4 L2.5 13.2 C2.5 12.5 2.9 11.9 3.6 11.6 Z";

    private static final String BIKE_FRAME =
            "M5.5 16.5 L9.5 10 L14.5 10 L18.5 16.5 M9.5 10 L13 16.5 L14.5 10 "
                    + "M13 16.5 L15.3 7 L18 7 M8.3 7.6 L11.8 7.6";

    private Icons() {
    }

    public static Node car(double size, String colorClass) {
        return shape(CAR_PATH, size, colorClass, "icon-fill");
    }

    public static Node bike(double size, String colorClass) {
        SVGPath frame = path(BIKE_FRAME, "icon-stroke", colorClass);
        Circle frontWheel = wheel(5.5, 16.5, "icon-stroke", colorClass);
        Circle rearWheel = wheel(18.5, 16.5, "icon-stroke", colorClass);
        return holder(new Group(frame, frontWheel, rearWheel), size);
    }

    public static Node lock(double size, String colorClass) {
        return shape("M7 10 V7.6 C7 4.8 9.2 2.6 12 2.6 C14.8 2.6 17 4.8 17 7.6 V10 "
                + "M5 10 H19 V20.5 H5 Z", size, colorClass, "icon-stroke");
    }

    public static Node check(double size, String colorClass) {
        return shape("M5 12.5 L9.5 17 L19.5 6.5", size, colorClass, "icon-stroke");
    }

    public static Node arrowRight(double size, String colorClass) {
        return shape("M4 12 H20 M14 6 L20 12 L14 18", size, colorClass, "icon-stroke");
    }

    public static Node arrowUp(double size, String colorClass) {
        return shape("M12 20 V4 M5 11 L12 4 L19 11", size, colorClass, "icon-stroke");
    }

    public static Node arrowLeft(double size, String colorClass) {
        return shape("M20 12 H4 M10 6 L4 12 L10 18", size, colorClass, "icon-stroke");
    }

    public static Node print(double size, String colorClass) {
        return shape("M7 9 V4 H17 V9 M7 18 H5 C4 18 3.5 17.5 3.5 16.5 V11.5 "
                + "C3.5 10.5 4 10 5 10 H19 C20 10 20.5 10.5 20.5 11.5 V16.5 "
                + "C20.5 17.5 20 18 19 18 H17 M7 14 H17 V21 H7 Z", size, colorClass, "icon-stroke");
    }

    public static Node clock(double size, String colorClass) {
        Circle dial = new Circle(12, 12, 8.5);
        dial.getStyleClass().addAll("icon-stroke", colorClass);
        SVGPath hands = path("M12 7.2 V12 L15.6 14.1", "icon-stroke", colorClass);
        return holder(new Group(dial, hands), size);
    }

    public static Node bolt(double size, String colorClass) {
        return shape("M13 2 L4.8 13.2 H11 L9.8 22 L19.2 9.8 H13 Z", size, colorClass, "icon-fill");
    }

    public static Node shield(double size, String colorClass) {
        return shape("M12 2.5 L20 5.5 V11.5 C20 16.4 16.6 20.1 12 21.5 "
                + "C7.4 20.1 4 16.4 4 11.5 V5.5 Z", size, colorClass, "icon-stroke");
    }

    public static Node backspace(double size, String colorClass) {
        return shape("M9 5 H21 V19 H9 L2.5 12 Z M12.5 9.5 L17.5 14.5 M17.5 9.5 L12.5 14.5",
                size, colorClass, "icon-stroke");
    }

    public static Node close(double size, String colorClass) {
        return shape("M6 6 L18 18 M18 6 L6 18", size, colorClass, "icon-stroke");
    }

    public static Node dashboard(double size, String colorClass) {
        return shape("M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z "
                + "M14 14 H21 V21 H14 Z", size, colorClass, "icon-stroke");
    }

    public static Node info(double size, String colorClass) {
        Circle dial = new Circle(12, 12, 9);
        dial.getStyleClass().addAll("icon-stroke", colorClass);
        SVGPath mark = path("M12 10.6 V16.4 M12 7.4 V7.8", "icon-stroke", colorClass);
        return holder(new Group(dial, mark), size);
    }

    public static Node user(double size, String colorClass) {
        Circle head = new Circle(12, 7.6, 3.8);
        head.getStyleClass().addAll("icon-stroke", colorClass);
        SVGPath shoulders = path("M4.8 20 C4.8 15.9 8 13.8 12 13.8 C16 13.8 19.2 15.9 19.2 20",
                "icon-stroke", colorClass);
        return holder(new Group(head, shoulders), size);
    }

    public static Node logout(double size, String colorClass) {
        return shape("M10 4 H5 V20 H10 M14 8 L18 12 L14 16 M9 12 H18", size, colorClass, "icon-stroke");
    }

    public static Node card(double size, String colorClass) {
        return shape("M3 6 H21 V18 H3 Z M3 10 H21 M7 14 H11", size, colorClass, "icon-stroke");
    }

    public static Node scan(double size, String colorClass) {
        return shape("M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15",
                size, colorClass, "icon-stroke");
    }

    public static Node ticket(double size, String colorClass) {
        return shape("M4 6 H20 V10 C18.7 10 17.8 10.9 17.8 12 C17.8 13.1 18.7 14 20 14 "
                + "V18 H4 V14 C5.3 14 6.2 13.1 6.2 12 C6.2 10.9 5.3 10 4 10 Z "
                + "M9 9 V15 M9 15 H9.1", size, colorClass, "icon-stroke");
    }

    public static Node steering(double size, String colorClass) {
        Circle rim = new Circle(12, 12, 8.5);
        rim.getStyleClass().addAll("icon-stroke", colorClass);
        SVGPath spokes = path("M12 3.5 V12 M5.2 16 L12 12 L18.8 16", "icon-stroke", colorClass);
        return holder(new Group(rim, spokes), size);
    }

    public static Node reset(double size, String colorClass) {
        return shape("M20 12 A8 8 0 1 1 17.4 6.3 M17.4 3.4 V6.9 H13.9",
                size, colorClass, "icon-stroke");
    }

    public static Node alert(double size, String colorClass) {
        return shape("M12 3 L22 20 H2 Z M12 9 V14.2 M12 17 V17.2",
                size, colorClass, "icon-stroke");
    }

    private static Circle wheel(double x, double y, String... classes) {
        Circle circle = new Circle(x, y, 3.2);
        circle.getStyleClass().addAll(classes);
        return circle;
    }

    private static SVGPath path(String content, String... classes) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.getStyleClass().addAll(classes);
        return p;
    }

    private static Node shape(String content, double size, String colorClass, String fillClass) {
        return holder(path(content, fillClass, colorClass), size);
    }

    private static Node holder(Node icon, double size) {
        icon.setMouseTransparent(true);
        icon.setScaleX(size / 24.0);
        icon.setScaleY(size / 24.0);
        StackPane holder = new StackPane(icon);
        holder.setMouseTransparent(true);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        holder.getStyleClass().add("icon-holder");
        return holder;
    }
}
