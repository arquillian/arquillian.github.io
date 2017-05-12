package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.MenuFragment;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class MainPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class='nav-collapse']")
    private WebElement menu;

    public MenuFragment menu() {
        String selector = "[class='nav'] li";
        String navigationSelector = "[class='nav']";
        return new MenuFragment(menu, selector, navigationSelector);
    }
}
