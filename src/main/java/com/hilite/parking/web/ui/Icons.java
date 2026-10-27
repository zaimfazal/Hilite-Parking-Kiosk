package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

/**
 * Small hand-drawn vector icon set (24 x 24 view box), carried over from the JavaFX build.
 *
 * <p>Each icon is an inline SVG inside a {@code <span aria-hidden="true">}: icons are always
 * decorative and every control that uses one also carries visible text or an
 * {@code aria-label}. The SVG markup is a compile-time constant (never user input), so setting
 * it as {@code innerHTML} cannot inject anything. Colour follows {@code currentColor}.</p>
 */
public final class Icons {

    private static final String CAR = "M4.6 11.2 L6.2 6.9 C6.6 5.8 7.6 5.1 8.8 5.1 L15.2 5.1 "
            + "C16.4 5.1 17.4 5.8 17.8 6.9 L19.4 11.2 L20.4 11.6 C21.1 11.9 21.5 12.5 21.5 13.2 "
            + "L21.5 16.4 C21.5 17 21.1 17.4 20.5 17.4 L19.4 17.4 L19.4 18.3 C19.4 18.8 19 19.2 "
            + "18.5 19.2 L17.6 19.2 C17.1 19.2 16.7 18.8 16.7 18.3 L16.7 17.4 L7.3 17.4 L7.3 18.3 "
            + "C7.3 18.8 6.9 19.2 6.4 19.2 L5.5 19.2 C5 19.2 4.6 18.8 4.6 18.3 L4.6 17.4 L3.5 17.4 "
            + "C2.9 17.4 2.5 17 2.5 16.4 L2.5 13.2 C2.5 12.5 2.9 11.9 3.6 11.6 Z";

    private Icons() {
    }

    public static Component car(int size) {
        return filled("<path d='" + CAR + "'/>", size, "car");
    }

    public static Component bike(int size) {
        return stroked("<path d='M5.5 16.5 L9.5 10 L14.5 10 L18.5 16.5 M9.5 10 L13 16.5 L14.5 10 "
                + "M13 16.5 L15.3 7 L18 7 M8.3 7.6 L11.8 7.6'/>"
                + "<circle cx='5.5' cy='16.5' r='3.2'/><circle cx='18.5' cy='16.5' r='3.2'/>",
                size, "bike");
    }

    public static Component lock(int size) {
        return path("M7 10 V7.6 C7 4.8 9.2 2.6 12 2.6 C14.8 2.6 17 4.8 17 7.6 V10 M5 10 H19 V20.5 H5 Z",
                size, "lock");
    }

    public static Component check(int size) {
        return path("M5 12.5 L9.5 17 L19.5 6.5", size, "check");
    }

    public static Component arrowRight(int size) {
        return path("M4 12 H20 M14 6 L20 12 L14 18", size, "arrow-right");
    }

    public static Component arrowUp(int size) {
        return path("M12 20 V4 M5 11 L12 4 L19 11", size, "arrow-up");
    }

    public static Component arrowLeft(int size) {
        return path("M20 12 H4 M10 6 L4 12 L10 18", size, "arrow-left");
    }

    public static Component print(int size) {
        return path("M7 9 V4 H17 V9 M7 18 H5 C4 18 3.5 17.5 3.5 16.5 V11.5 C3.5 10.5 4 10 5 10 "
                + "H19 C20 10 20.5 10.5 20.5 11.5 V16.5 C20.5 17.5 20 18 19 18 H17 "
                + "M7 14 H17 V21 H7 Z", size, "print");
    }

    public static Component clock(int size) {
        return stroked("<circle cx='12' cy='12' r='8.5'/><path d='M12 7.2 V12 L15.6 14.1'/>",
                size, "clock");
    }

    public static Component bolt(int size) {
        return filled("<path d='M13 2 L4.8 13.2 H11 L9.8 22 L19.2 9.8 H13 Z'/>", size, "bolt");
    }

    public static Component shield(int size) {
        return path("M12 2.5 L20 5.5 V11.5 C20 16.4 16.6 20.1 12 21.5 C7.4 20.1 4 16.4 4 11.5 "
                + "V5.5 Z", size, "shield");
    }

    public static Component backspace(int size) {
        return path("M9 5 H21 V19 H9 L2.5 12 Z M12.5 9.5 L17.5 14.5 M17.5 9.5 L12.5 14.5",
                size, "backspace");
    }

    public static Component close(int size) {
        return path("M6 6 L18 18 M18 6 L6 18", size, "close");
    }

    public static Component dashboard(int size) {
        return path("M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z",
                size, "dashboard");
    }

    public static Component info(int size) {
        return stroked("<circle cx='12' cy='12' r='9'/><path d='M12 10.6 V16.4 M12 7.4 V7.8'/>",
                size, "info");
    }

    public static Component logout(int size) {
        return path("M10 4 H5 V20 H10 M14 8 L18 12 L14 16 M9 12 H18", size, "logout");
    }

    public static Component card(int size) {
        return path("M3 6 H21 V18 H3 Z M3 10 H21 M7 14 H11", size, "card");
    }

    public static Component scan(int size) {
        return path("M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15", size, "scan");
    }

    public static Component steering(int size) {
        return stroked("<circle cx='12' cy='12' r='8.5'/>"
                + "<path d='M12 3.5 V12 M5.2 16 L12 12 L18.8 16'/>", size, "steering");
    }

    public static Component reset(int size) {
        return path("M20 12 A8 8 0 1 1 17.4 6.3 M17.4 3.4 V6.9 H13.9", size, "reset");
    }

    public static Component alert(int size) {
        return path("M12 3 L22 20 H2 Z M12 9 V14.2 M12 17 V17.2", size, "alert");
    }

    private static Component path(String d, int size, String name) {
        return stroked("<path d='" + d + "'/>", size, name);
    }

    private static Component stroked(String body, int size, String name) {
        return svg("<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.7' "
                + "stroke-linecap='round' stroke-linejoin='round' focusable='false'>"
                + body + "</svg>", size, name);
    }

    private static Component filled(String body, int size, String name) {
        return svg("<svg viewBox='0 0 24 24' fill='currentColor' focusable='false'>" + body
                + "</svg>", size, name);
    }

    private static Component svg(String markup, int size, String name) {
        Span holder = new Span();
        holder.addClassNames("icon", "icon-" + name);
        holder.getElement().setAttribute("aria-hidden", "true");
        holder.getStyle().set("--icon-size", size + "px");
        holder.getElement().setProperty("innerHTML", markup);
        return holder;
    }
}
