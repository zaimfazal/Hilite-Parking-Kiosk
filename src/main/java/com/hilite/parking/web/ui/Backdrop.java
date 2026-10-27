package com.hilite.parking.web.ui;

import com.vaadin.flow.component.html.Div;

/**
 * The decorative garage scene behind every kiosk screen.
 *
 * <p>A one-to-one port of the JavaFX {@code GarageBackdrop} canvas drawing to a single inline
 * SVG (1600 x 900 design box, cover-cropped): base gradient, warm and blue glows, two pillars,
 * ceiling light strips, a perspective floor with lane marks and a gold kerb line, a diagonal
 * light shaft, a ghost SUV silhouette and two vignettes. It uses no images or remote assets,
 * never takes input and is hidden from assistive technology.</p>
 */
public final class Backdrop extends Div {

    private static final String SVG = "<svg viewBox='0 0 1600 900' preserveAspectRatio='xMidYMid slice'"
            + " xmlns='http://www.w3.org/2000/svg' focusable='false'>"
            + "<defs>"
            + "<linearGradient id='gbBase' x1='0' y1='0' x2='0.25' y2='1'>"
            + "<stop offset='0' stop-color='#121b26'/><stop offset='0.45' stop-color='#0a1019'/>"
            + "<stop offset='1' stop-color='#05080d'/></linearGradient>"
            + "<radialGradient id='gbWarm' cx='0.18' cy='0.12' r='0.58'>"
            + "<stop offset='0' stop-color='rgb(214,176,112)' stop-opacity='0.22'/>"
            + "<stop offset='0.5' stop-color='rgb(214,176,112)' stop-opacity='0.06'/>"
            + "<stop offset='1' stop-color='rgb(214,176,112)' stop-opacity='0'/></radialGradient>"
            + "<radialGradient id='gbBlue' cx='0.86' cy='0.28' r='0.5'>"
            + "<stop offset='0' stop-color='rgb(59,130,246)' stop-opacity='0.14'/>"
            + "<stop offset='1' stop-color='rgb(59,130,246)' stop-opacity='0'/></radialGradient>"
            + "<linearGradient id='gbSheen' x1='0' y1='0' x2='1' y2='0'>"
            + "<stop offset='0' stop-color='#fff' stop-opacity='0'/>"
            + "<stop offset='0.78' stop-color='#fff' stop-opacity='0.035'/>"
            + "<stop offset='1' stop-color='#fff' stop-opacity='0'/></linearGradient>"
            + "<linearGradient id='gbFloor' x1='0' y1='0' x2='0' y2='1'>"
            + "<stop offset='0' stop-color='#0b1119'/><stop offset='1' stop-color='#070b10'/></linearGradient>"
            + "<linearGradient id='gbGold' x1='0' y1='0' x2='1' y2='0'>"
            + "<stop offset='0' stop-color='rgb(242,196,122)' stop-opacity='0'/>"
            + "<stop offset='0.15' stop-color='rgb(242,196,122)' stop-opacity='0.55'/>"
            + "<stop offset='0.6' stop-color='rgb(242,196,122)' stop-opacity='0.2'/>"
            + "<stop offset='1' stop-color='rgb(242,196,122)' stop-opacity='0'/></linearGradient>"
            + "<linearGradient id='gbShaft' gradientUnits='userSpaceOnUse' x1='160' y1='-90' x2='896' y2='702'>"
            + "<stop offset='0' stop-color='#fff' stop-opacity='0.10'/>"
            + "<stop offset='0.45' stop-color='#fff' stop-opacity='0.015'/>"
            + "<stop offset='1' stop-color='#fff' stop-opacity='0'/></linearGradient>"
            + "<linearGradient id='gbCar' x1='0' y1='0' x2='0' y2='1'>"
            + "<stop offset='0' stop-color='rgb(204,222,244)' stop-opacity='0.13'/>"
            + "<stop offset='0.55' stop-color='rgb(145,172,202)' stop-opacity='0.08'/>"
            + "<stop offset='1' stop-color='rgb(60,82,108)' stop-opacity='0.04'/></linearGradient>"
            + "<radialGradient id='gbLamp'>"
            + "<stop offset='0' stop-color='rgb(180,220,255)' stop-opacity='0.22'/>"
            + "<stop offset='1' stop-color='rgb(180,220,255)' stop-opacity='0'/></radialGradient>"
            + "<linearGradient id='gbBottom' x1='0' y1='0' x2='0' y2='1'>"
            + "<stop offset='0' stop-color='#000' stop-opacity='0'/>"
            + "<stop offset='1' stop-color='#000' stop-opacity='0.62'/></linearGradient>"
            + "<radialGradient id='gbVignette' cx='0.5' cy='0.45' r='0.9'>"
            + "<stop offset='0' stop-color='rgb(2,5,9)' stop-opacity='0'/>"
            + "<stop offset='0.7' stop-color='rgb(2,5,9)' stop-opacity='0.10'/>"
            + "<stop offset='1' stop-color='rgb(2,5,9)' stop-opacity='0.55'/></radialGradient>"
            + "</defs>"
            + "<rect width='1600' height='900' fill='url(#gbBase)'/>"
            + "<rect width='1600' height='900' fill='url(#gbWarm)'/>"
            + "<rect width='1600' height='900' fill='url(#gbBlue)'/>"
            // pillars
            + "<rect x='72' y='144' width='136' height='720' fill='rgba(8,13,20,0.82)'/>"
            + "<rect x='72' y='144' width='136' height='720' fill='url(#gbSheen)'/>"
            + "<rect x='1312' y='45' width='176' height='810' fill='rgba(9,15,23,0.72)'/>"
            + "<rect x='1321.6' y='45' width='1.5' height='810' fill='rgba(255,255,255,0.028)'/>"
            // ceiling light strips: halo, glow, core
            + light(256, 85.5, 672, "242,196,122", 0.42)
            + light(208, 139.5, 480, "255,222,168", 0.20)
            + light(992, 49.5, 368, "255,255,255", 0.12)
            + "<rect y='184.5' width='1600' height='1.8' fill='rgba(255,255,255,0.035)'/>"
            // floor
            + "<rect y='567' width='1600' height='333' fill='url(#gbFloor)'/>"
            + "<g stroke='rgba(255,255,255,0.035)' stroke-width='1'>"
            + "<line x1='832' y1='522' x2='0' y2='900'/><line x1='832' y1='522' x2='256' y2='900'/>"
            + "<line x1='832' y1='522' x2='544' y2='900'/><line x1='832' y1='522' x2='832' y2='900'/>"
            + "<line x1='832' y1='522' x2='1120' y2='900'/><line x1='832' y1='522' x2='1408' y2='900'/>"
            + "<line x1='832' y1='522' x2='1600' y2='900'/>"
            + "<line x1='0' y1='666' x2='1600' y2='666'/><line x1='0' y1='774' x2='1600' y2='774'/></g>"
            + "<g fill='rgba(255,255,255,0.055)'>"
            + "<polygon points='448,684 467.2,684 512,733.5 489.6,733.5'/>"
            + "<polygon points='672,684 691.2,684 736,733.5 713.6,733.5'/>"
            + "<polygon points='896,684 915.2,684 960,733.5 937.6,733.5'/>"
            + "<polygon points='1120,684 1139.2,684 1184,733.5 1161.6,733.5'/></g>"
            + "<rect y='724.5' width='1088' height='2' fill='url(#gbGold)'/>"
            + "<line x1='160' y1='-90' x2='896' y2='702' stroke='url(#gbShaft)' stroke-width='96'"
            + " stroke-linecap='round'/>"
            // ghost SUV, 1000-unit car box scaled to 40% of the width
            + "<g transform='translate(88 515.2) scale(0.64)'>"
            + "<ellipse cx='490' cy='310' rx='450' ry='24' fill='rgba(0,0,0,0.35)'/>"
            + "<path d='M40 300 L58 242 Q76 220 118 214 L230 198 Q286 122 368 108 L610 102 "
            + "Q702 106 764 152 L856 166 Q928 178 956 226 L970 268 L970 300 L912 300 "
            + "A62 62 0 0 0 788 300 L268 300 A62 62 0 0 0 144 300 Z' fill='url(#gbCar)'"
            + " stroke='rgba(212,228,246,0.17)' stroke-width='2'/>"
            + "<polygon points='270,192 360,126 600,124 660,154 620,186 350,190' fill='rgba(13,22,32,0.5)'"
            + " stroke='rgba(220,235,250,0.10)' stroke-width='1.5'/>"
            + "<polygon points='682,148 748,148 838,180 800,194 700,184 670,160' fill='rgba(13,22,32,0.5)'/>"
            + "<g fill='rgba(4,8,13,0.8)' stroke='rgba(190,209,230,0.18)' stroke-width='2'>"
            + "<circle cx='208' cy='306' r='62'/><circle cx='848' cy='306' r='62'/></g>"
            + "<g fill='none' stroke='rgba(190,209,230,0.09)' stroke-width='2'>"
            + "<circle cx='208' cy='306' r='42'/><circle cx='848' cy='306' r='42'/></g>"
            + "<g stroke='rgba(210,228,248,0.10)' stroke-width='1.5'>"
            + "<line x1='510' y1='192' x2='510' y2='286'/><line x1='720' y1='178' x2='730' y2='284'/>"
            + "<line x1='300' y1='232' x2='430' y2='227'/></g>"
            + "<ellipse cx='955' cy='265' rx='75' ry='55' fill='url(#gbLamp)'/>"
            + "</g>"
            + "<rect y='495' width='1600' height='405' fill='url(#gbBottom)'/>"
            + "<rect width='1600' height='900' fill='url(#gbVignette)'/>"
            + "</svg>";

    public Backdrop() {
        addClassName("garage-backdrop");
        getElement().setAttribute("aria-hidden", "true");
        getElement().setProperty("innerHTML", SVG);
    }

    private static String light(double x, double y, double length, String rgb, double alpha) {
        String line = "<line x1='" + x + "' y1='" + y + "' x2='" + (x + length) + "' y2='" + y
                + "' stroke-linecap='round' stroke='rgb(" + rgb + ")' ";
        return line + "stroke-opacity='" + round(alpha * 0.10) + "' stroke-width='22'/>"
                + line + "stroke-opacity='" + round(alpha * 0.28) + "' stroke-width='7'/>"
                + line + "stroke-opacity='" + alpha + "' stroke-width='2'/>";
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
