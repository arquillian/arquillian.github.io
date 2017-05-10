package org.arquillian.tests.pom;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class MainPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class='nav-collapse']")
    private WebElement menu;

    private String selector = "[class='nav'] li";

    public MenuFragment menu() {
        return new MenuFragment(menu, selector);
    }
}
