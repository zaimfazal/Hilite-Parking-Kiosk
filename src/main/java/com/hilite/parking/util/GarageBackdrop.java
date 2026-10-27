package com.hilite.parking.util;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;

/**
 * Decorative vector garage scene used behind kiosk screens. No external photography is required.
 * It draws once per resize and never reacts to input.
 */
public final class GarageBackdrop extends StackPane {

    private final Canvas canvas = new Canvas();

    public GarageBackdrop() {
        setMouseTransparent(true);
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((observable, oldValue, newValue) -> draw());
        heightProperty().addListener((observable, oldValue, newValue) -> draw());
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w < 20 || h < 20) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        g.setFill(new LinearGradient(0, 0, 0.25, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#121b26")),
                new Stop(0.45, Color.web("#0a1019")),
                new Stop(1, Color.web("#05080d"))));
        g.fillRect(0, 0, w, h);

        g.setFill(new RadialGradient(0, 0, 0.18, 0.12, 0.58, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(214, 176, 112, 0.22)),
                new Stop(0.5, Color.rgb(214, 176, 112, 0.06)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);

        g.setFill(new RadialGradient(0, 0, 0.86, 0.28, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(59, 130, 246, 0.14)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);

        drawArchitecture(g, w, h);
        drawCar(g, w, h);

        g.setFill(new LinearGradient(0, 0.55, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(1, Color.rgb(0, 0, 0, 0.62))));
        g.fillRect(0, h * 0.55, w, h * 0.45);

        g.setFill(new RadialGradient(0, 0, 0.5, 0.45, 0.9, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.7, Color.rgb(2, 5, 9, 0.10)),
                new Stop(1, Color.rgb(2, 5, 9, 0.55))));
        g.fillRect(0, 0, w, h);
    }

    private void drawArchitecture(GraphicsContext g, double w, double h) {
        g.setFill(Color.rgb(8, 13, 20, 0.82));
        g.fillRect(w * 0.045, h * 0.16, w * 0.085, h * 0.8);
        g.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.78, Color.rgb(255, 255, 255, 0.035)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(w * 0.045, h * 0.16, w * 0.085, h * 0.8);

        g.setFill(Color.rgb(9, 15, 23, 0.72));
        g.fillRect(w * 0.82, h * 0.05, w * 0.11, h * 0.9);
        g.setFill(Color.rgb(255, 255, 255, 0.028));
        g.fillRect(w * 0.826, h * 0.05, 1.5, h * 0.9);

        drawLight(g, w * 0.16, h * 0.095, w * 0.42, Color.rgb(242, 196, 122, 0.42));
        drawLight(g, w * 0.13, h * 0.155, w * 0.30, Color.rgb(255, 222, 168, 0.20));
        drawLight(g, w * 0.62, h * 0.055, w * 0.23, Color.rgb(255, 255, 255, 0.12));

        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.035)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, h * 0.205, w, h * 0.002);

        g.setFill(new LinearGradient(0, 0.62, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0b1119")),
                new Stop(1, Color.web("#070b10"))));
        g.fillRect(0, h * 0.63, w, h * 0.37);

        g.setStroke(Color.rgb(255, 255, 255, 0.035));
        g.setLineWidth(1);
        double vx = w * 0.52;
        double vy = h * 0.58;
        for (double x : new double[]{w * 0.0, w * 0.16, w * 0.34, w * 0.52, w * 0.7, w * 0.88, w}) {
            g.strokeLine(vx, vy, x, h);
        }
        g.strokeLine(0, h * 0.74, w, h * 0.74);
        g.strokeLine(0, h * 0.86, w, h * 0.86);

        g.setFill(Color.rgb(255, 255, 255, 0.055));
        for (int i = 0; i < 4; i++) {
            double x = w * (0.28 + i * 0.14);
            g.fillPolygon(new double[]{x, x + w * 0.012, x + w * 0.04, x + w * 0.026},
                    new double[]{h * 0.76, h * 0.76, h * 0.815, h * 0.815}, 4);
        }

        g.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.15, Color.rgb(242, 196, 122, 0.55)),
                new Stop(0.6, Color.rgb(242, 196, 122, 0.20)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, h * 0.805, w * 0.68, 2);

        g.setStroke(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.10)),
                new Stop(0.45, Color.rgb(255, 255, 255, 0.015)),
                new Stop(1, Color.TRANSPARENT)));
        g.setLineWidth(w * 0.06);
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeLine(w * 0.10, -h * 0.1, w * 0.56, h * 0.78);
    }

    private void drawLight(GraphicsContext g, double x, double y, double length, Color color) {
        g.setLineCap(StrokeLineCap.ROUND);
        g.setStroke(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * 0.10));
        g.setLineWidth(22);
        g.strokeLine(x, y, x + length, y);
        g.setStroke(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * 0.28));
        g.setLineWidth(7);
        g.strokeLine(x, y, x + length, y);
        g.setStroke(color);
        g.setLineWidth(2);
        g.strokeLine(x, y, x + length, y);
    }

    private void drawCar(GraphicsContext g, double w, double h) {
        double scale = w * 0.40 / 1000.0;
        double baseX = w * 0.055;
        double baseY = h * 0.80;
        g.save();
        g.translate(baseX, baseY - 320 * scale);
        g.scale(scale, scale);

        g.setFill(Color.rgb(0, 0, 0, 0.35));
        g.fillOval(40, 286, 900, 48);

        g.beginPath();
        g.moveTo(40, 300);
        g.lineTo(58, 242);
        g.quadraticCurveTo(76, 220, 118, 214);
        g.lineTo(230, 198);
        g.quadraticCurveTo(286, 122, 368, 108);
        g.lineTo(610, 102);
        g.quadraticCurveTo(702, 106, 764, 152);
        g.lineTo(856, 166);
        g.quadraticCurveTo(928, 178, 956, 226);
        g.lineTo(970, 268);
        g.lineTo(970, 300);
        g.lineTo(912, 300);
        g.arc(850, 300, 62, 62, 0, -180);
        g.lineTo(268, 300);
        g.arc(206, 300, 62, 62, 0, -180);
        g.closePath();
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(204, 222, 244, 0.13)),
                new Stop(0.55, Color.rgb(145, 172, 202, 0.08)),
                new Stop(1, Color.rgb(60, 82, 108, 0.04))));
        g.fill();
        g.setStroke(Color.rgb(212, 228, 246, 0.17));
        g.setLineWidth(2);
        g.stroke();

        g.setFill(Color.rgb(13, 22, 32, 0.5));
        g.fillPolygon(new double[]{270, 360, 600, 660, 620, 350},
                new double[]{192, 126, 124, 154, 186, 190}, 6);
        g.fillPolygon(new double[]{682, 748, 838, 800, 700, 670},
                new double[]{148, 148, 180, 194, 184, 160}, 6);
        g.setStroke(Color.rgb(220, 235, 250, 0.10));
        g.setLineWidth(1.5);
        g.strokePolygon(new double[]{270, 360, 600, 660, 620, 350},
                new double[]{192, 126, 124, 154, 186, 190}, 6);

        g.setFill(Color.rgb(4, 8, 13, 0.8));
        g.fillOval(146, 244, 124, 124);
        g.fillOval(786, 244, 124, 124);
        g.setStroke(Color.rgb(190, 209, 230, 0.18));
        g.setLineWidth(2);
        g.strokeOval(146, 244, 124, 124);
        g.strokeOval(786, 244, 124, 124);
        g.setStroke(Color.rgb(190, 209, 230, 0.09));
        g.strokeOval(166, 264, 84, 84);
        g.strokeOval(806, 264, 84, 84);

        g.setStroke(Color.rgb(210, 228, 248, 0.10));
        g.setLineWidth(1.5);
        g.strokeLine(510, 192, 510, 286);
        g.strokeLine(720, 178, 730, 284);
        g.strokeLine(300, 232, 430, 227);

        g.setFill(new RadialGradient(0, 0, 0.92, 0.6, 0.18, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(180, 220, 255, 0.22)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(880, 210, 150, 110);
        g.restore();
    }
}
