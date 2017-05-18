package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.NavigationListFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class ModulesPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class='nav nav-list']")
    private NavigationListFragment navList;

    public NavigationListFragment navigationList() {
        return navList;
    }

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }
}
