package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.NavigationListFragment;
import org.arquillian.tests.pom.fragmentObjects.SideBarFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class GuidesPage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "content")
    private WebElement navList;

    @FindBy(id = "sidebar")
    private WebElement sidebar;

    public NavigationListFragment navigationList() {
        String selector = "dl a";
        String navigationSelector = "dl";
        return new NavigationListFragment(navList, selector, navigationSelector);
    }

    public SideBarFragment sidebar() {
        String selector = "h3";
        String navigationSelector = "h3";
        return new SideBarFragment(sidebar, selector, navigationSelector);
    }

    public GuidesPageVerifier verify() {
        return new GuidesPageVerifier(driver);
    }

    public class GuidesPageVerifier extends PageVerifier {
        public GuidesPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
