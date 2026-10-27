package com.hilite.parking;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Web entry point for HiLITE Mall Smart Parking.
 *
 * <p>Spring Boot hosts the Java business layer (allocation, fees, exit, statistics, reset) and
 * Vaadin renders the kiosk and admin views from Java. Lumo supplies the base component styling
 * and {@code styles.css} carries the HiLITE design system.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
@Meta(name = "viewport", content = "width=device-width, initial-scale=1")
public class HiliteParkingApplication implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("HiLITE Mall Smart Parking");
    }

    public static void main(String[] args) {
        SpringApplication.run(HiliteParkingApplication.class, args);
    }
}
