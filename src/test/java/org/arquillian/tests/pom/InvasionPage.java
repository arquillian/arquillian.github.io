package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class InvasionPage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "sidebar")
    private WebElement sidebar;

    private String selector = "[class='nav'] li";

    public SideBarFragment sidebar() {
        return new SideBarFragment(sidebar, selector);

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
