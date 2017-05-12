package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.BlogFragment;
import org.arquillian.tests.pom.fragmentObjects.SideBarFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class BlogPage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "content")
    private BlogFragment blogs;

    @FindBy(id = "sidebar")
    private WebElement sidebar;

    public BlogFragment blogs() {
        return blogs;
    }

    public SideBarFragment sidebar() {
        String selector = "h2";
        String navigationSelector = "h2";
        return new SideBarFragment(sidebar, selector, navigationSelector);
    }

    public BlogPageVerifier verify() {
        return new BlogPageVerifier(driver);
    }

    public class BlogPageVerifier extends PageVerifier {
        public BlogPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
