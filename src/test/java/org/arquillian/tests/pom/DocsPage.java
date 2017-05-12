package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class DocsPage {
    
    @Drone
    private WebDriver driver;

    @FindBy(className = "well")
    private WebElement content;

    private String selector = "a";

    public ContentFragment content() {
        return new ContentFragment(content, selector);
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
