package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class GuidesPage {

    @Drone
    private WebDriver driver;

    public GuidesPageVerifier verify() {
        return new GuidesPageVerifier(driver);
    }

    public class GuidesPageVerifier extends PageVerifier {
        public GuidesPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
