package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class StandaloneDocumentationPage {
    @Drone
    private WebDriver driver;

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }
}
