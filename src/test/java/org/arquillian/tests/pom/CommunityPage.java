package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class CommunityPage {
    @Drone
    private WebDriver driver;

    public CommunityPageVerifier verify() {
        return new CommunityPageVerifier(driver);
    }

    public class CommunityPageVerifier extends PageVerifier {
        public CommunityPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
