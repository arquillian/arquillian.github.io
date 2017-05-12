package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.NavigationListFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class ModulesPage {

    @Drone
    private WebDriver driver;

    @FindBy(className = "well")
    private WebElement navList;

    public NavigationListFragment navigationList() {
        String selector = "[class='nav nav-list'] li a";
        String navigationSelector = "[class='nav nav-list']";
        return new NavigationListFragment(navList, selector, navigationSelector);
    }

    public ModulesPageVerifier verify() {
        return new ModulesPageVerifier(driver);
    }

    public class ModulesPageVerifier extends PageVerifier {
        public ModulesPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
