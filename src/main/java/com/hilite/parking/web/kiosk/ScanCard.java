package com.hilite.parking.web.kiosk;

import com.hilite.parking.util.FormatUtil;
import com.hilite.parking.web.ui.Icons;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * The simulated number-plate scanner card shared by Entry and Exit.
 *
 * <p>The sweeping beam and the progress fill are pure CSS animations whose duration comes from
 * the {@code --scan-ms} custom property; the server decides when the scan is complete and then
 * switches the card to its finished state. Nothing here touches a camera.</p>
 */
final class ScanCard extends Div {

    static final String PLATE_PLACEHOLDER = "•••• •• ••••";

    private final Div iconTile = new Div();
    private final Span title = new Span("Scanning number plate…");
    private final Span subtitle = new Span("Please wait while we read your vehicle number");
    private final Span preview = new Span(PLATE_PLACEHOLDER);
    private final Div progress = new Div();

    ScanCard(int scanMillis, String... extraClasses) {
        addClassName("scan-card");
        addClassNames(extraClasses);
        getStyle().set("--scan-ms", scanMillis + "ms");

        iconTile.addClassName("scan-icon-tile");
        iconTile.add(Icons.scan(21));
        title.addClassName("scan-title");
        subtitle.addClassName("scan-subtitle");
        Div copy = new Div(title, subtitle);
        copy.addClassName("scan-copy");
        // Announce the scan status changes politely.
        copy.getElement().setAttribute("aria-live", "polite");
        Div head = new Div(iconTile, copy);
        head.addClassName("scan-head");

        preview.addClassName("plate-preview-text");
        preview.getElement().setAttribute("aria-hidden", "true");
        Div beam = new Div();
        beam.addClassName("scan-beam");
        Div frame = new Div(preview, beam, corner("tl"), corner("tr"), corner("bl"), corner("br"));
        frame.addClassName("plate-frame");
        frame.getElement().setAttribute("aria-hidden", "true");

        Div bar = new Div();
        bar.addClassName("scan-progress-bar");
        progress.addClassName("scan-progress");
        progress.add(bar);
        progress.getElement().setAttribute("role", "progressbar");
        progress.getElement().setAttribute("aria-label", "Simulated scan progress");
        progress.getElement().setAttribute("aria-valuemin", "0");
        progress.getElement().setAttribute("aria-valuemax", "100");
        progress.getElement().setAttribute("aria-valuetext", "Scanning");

        add(head, frame, progress);
    }

    /** Switches to the finished state and shows the scanned plate. */
    void complete(String subtitleText, String plate) {
        addClassName("is-complete");
        iconTile.removeAll();
        iconTile.add(Icons.check(20));
        iconTile.addClassName("is-success");
        title.setText("Number plate ready");
        subtitle.setText(subtitleText);
        progress.getElement().setAttribute("aria-valuenow", "100");
        progress.getElement().setAttribute("aria-valuetext", "Scan complete");
        showPlate(plate);
    }

    /** Mirrors the plate field into the illustrated plate (display normalization only). */
    void showPlate(String raw) {
        String normalized = FormatUtil.normalizePlate(raw);
        preview.setText(normalized.isEmpty() ? PLATE_PLACEHOLDER : normalized);
    }

    String titleText() {
        return title.getText();
    }

    String previewText() {
        return preview.getText();
    }

    private static Div corner(String position) {
        Div corner = new Div();
        corner.addClassNames("scan-corner", "corner-" + position);
        return corner;
    }
}
