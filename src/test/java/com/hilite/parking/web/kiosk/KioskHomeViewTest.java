package com.hilite.parking.web.kiosk;

import com.hilite.parking.model.KioskMode;
import com.hilite.parking.service.AdminAccessService;
import com.hilite.parking.service.DemoResetService;
import com.hilite.parking.web.admin.AdminPinView;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ViewPackages(packages = "com.hilite.parking.web")
class KioskHomeViewTest extends SpringBrowserlessTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("hilite.db-path", () -> tempDir.resolve("kiosk-home.db").toString());
    }

    @Autowired
    DemoResetService demoReset;

    @Autowired
    AdminAccessService adminAccess;

    @BeforeEach
    void cleanDatabase() {
        demoReset.reset();
    }

    @Test
    void homeForwardsToEntryByDefault() {
        navigate("", EntryView.class);
        assertInstanceOf(EntryView.class, getCurrentView());
    }

    @Test
    void homeForwardsToExitWhenModeIsExit() {
        adminAccess.changeMode(KioskMode.EXIT);

        navigate("", ExitView.class);

        assertInstanceOf(ExitView.class, getCurrentView());
    }

    @Test
    void ctrlShiftAOpensTheAdminPinScreen() {
        navigate(EntryView.class);

        fireShortcut(Key.KEY_A, KeyModifier.CONTROL, KeyModifier.SHIFT);

        assertInstanceOf(AdminPinView.class, getCurrentView());
    }
}
