package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.MenuFragment;
import org.arquillian.tests.pom.fragmentObjects.NavigationListFragment;
import org.arquillian.tests.pom.fragmentObjects.TabPaneContentFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class DocsPage {

    @Drone
    private WebDriver driver;

    @FindBy(className = "well")
    private WebElement navList;

    @FindBy(className = "tabbable")
    private WebElement menu;

    @FindBy(className = "tab-content")
    private TabPaneContentFragment tabContent;


    public NavigationListFragment navigationList() {
        String selector = "[class='nav nav-list'] li a";
        String navigationSelector = "[class='nav nav-list']";
        return new NavigationListFragment(navList, selector, navigationSelector);
    }

    public MenuFragment menu() {
        String selector = "[class='nav nav-pills'] li a";
        String navigationSelector = "[class='nav nav-pills']";

        return new MenuFragment(menu, selector, navigationSelector);
    }

    public TabPaneContentFragment content() {
        return tabContent;
    }
    public DocsPageVerifier verify() {
        return new DocsPageVerifier(driver);
    }

    public class DocsPageVerifier extends PageVerifier {
        public DocsPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
