package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.MenuFragment;
import org.arquillian.tests.pom.fragmentObjects.NavigationListFragment;
import org.arquillian.tests.pom.fragmentObjects.TabPaneContentFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class DocsPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[class ='nav nav-list']")
    private NavigationListFragment navList;

    @FindBy(css = "[class = 'nav nav-pills']")
    private MenuFragment menu;

    @FindBy(css = "[class = 'tab-content']")
    private TabPaneContentFragment tabContent;

    public NavigationListFragment navigationList() {
        return navList;
    }

    public MenuFragment menu() {
        return menu;
    }

    public TabPaneContentFragment content() {
        return tabContent;
    }

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }
}
