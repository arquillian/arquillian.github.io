package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.BlogFragment;
import org.arquillian.tests.pom.fragmentObjects.SideBarFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class BlogPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[id = 'content']")
    private BlogFragment blogContent;

    @FindBy(css = "[id = 'sidebar']")
    private SideBarFragment sidebar;

    public BlogFragment blogContent() {
        return blogContent;
    }

    public SideBarFragment sidebar() {
        return sidebar;
    }

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }
}
