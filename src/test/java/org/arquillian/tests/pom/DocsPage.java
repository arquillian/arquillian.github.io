package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class DocsPage {
    
    @Drone
    private WebDriver driver;

    public DocsPageVerifier verify() {
        return new DocsPageVerifier(driver);
    }

    public class DocsPageVerifier extends PageVerifier {
        public DocsPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
