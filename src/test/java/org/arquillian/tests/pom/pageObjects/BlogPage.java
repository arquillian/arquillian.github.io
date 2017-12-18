package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.BlogFragment;
import org.arquillian.tests.pom.fragmentObjects.CloudTagFragment;
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

    @FindBy(css = "[class = 'tag-cloud']")
    private CloudTagFragment cloudTag;

    public BlogFragment releaseBlog() {
        return blogContent;
    }

    public BlogFragment nonReleaseBlog() {
        return blogContent;
    }

    public SideBarFragment sidebar() {
        return sidebar;
    }

    public CloudTagFragment cloudTag() {
        return cloudTag;
    }

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }

    public BlogFragment newAnnouncementBanner() {
        return blogContent;
    }
}
