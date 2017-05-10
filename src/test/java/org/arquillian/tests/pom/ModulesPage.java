package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class ModulesPage {
    
    @Drone
    private WebDriver driver;

    public ModulesPageVerifier verify() {
        return new ModulesPageVerifier(driver);
    }

    public class ModulesPageVerifier extends PageVerifier {
        public ModulesPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
