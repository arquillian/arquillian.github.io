package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.DescriptionListFragment;
import org.arquillian.tests.pom.fragmentObjects.SideBarFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class GuidesPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[id = 'content']")
    private DescriptionListFragment descList;

    @FindBy(css = "[id = 'sidebar']")
    private SideBarFragment sidebar;

    public DescriptionListFragment descriptionList() {
        return descList;
    }

    public SideBarFragment sidebar() {
        return sidebar;
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
