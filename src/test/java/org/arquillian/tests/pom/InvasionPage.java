package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class InvasionPage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "sidebar")
    private SideBarFragment sidebar;

    public SideBarFragment sidebar() {
        return sidebar;
    }

    public InvasionPageVerifier verify() {
        return new InvasionPageVerifier(driver);
    }

    public class InvasionPageVerifier extends PageVerifier {
        public InvasionPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
