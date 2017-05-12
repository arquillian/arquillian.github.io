package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.SideBarFragment;
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

    public SideBarFragment sidebar() {
        String selector = "[class='nav'] li";
        String navigationSelector = "[class='nav']";
        return new SideBarFragment(sidebar, selector, navigationSelector);
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
