package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.ContentFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class CommunityPage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[id = 'content']")
    private ContentFragment content;

    public ContentFragment content() {
        return content;
    }

    public PageVerifier verify() {
        return new PageVerifier(driver);
    }
}
