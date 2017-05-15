package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.MenuFragment;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class MainPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class='nav']")
    private MenuFragment menu;

    public MenuFragment menu() {
        return menu;
    }
}
