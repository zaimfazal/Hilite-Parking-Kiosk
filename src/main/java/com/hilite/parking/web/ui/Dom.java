package com.hilite.parking.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Tiny builders for styled spans and divs. Text is always set as plain text. */
public final class Dom {

    private Dom() {
    }

    /** A span with plain text and space-separated CSS classes. */
    public static Span text(String text, String classNames) {
        Span span = new Span(text);
        span.addClassNames(classNames.split(" "));
        return span;
    }

    /** A div with space-separated CSS classes and children. */
    public static Div box(String classNames, Component... children) {
        Div div = new Div(children);
        div.addClassNames(classNames.split(" "));
        return div;
    }
}
