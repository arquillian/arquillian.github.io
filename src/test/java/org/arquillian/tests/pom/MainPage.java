package org.arquillian.tests.pom;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class MainPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class='nav-collapse']")
    private MenuFragment menu;

    public MenuFragment menu() {
        return menu;
    }
}
