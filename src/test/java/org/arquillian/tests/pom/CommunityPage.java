package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class CommunityPage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "content")
    private WebElement content;

    private String selector = "[id='section'] h2";

    public ContentFragment content() {
        return new ContentFragment(content, selector);
    }

    public CommunityPageVerifier verify() {
        return new CommunityPageVerifier(driver);
    }

    public class CommunityPageVerifier extends PageVerifier {
        public CommunityPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
