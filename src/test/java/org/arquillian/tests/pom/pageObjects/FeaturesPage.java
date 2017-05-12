package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.pom.fragmentObjects.ContentFragment;
import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class FeaturesPage {

    @Drone
    private WebDriver driver;

    @FindBy(className = "features")
    private WebElement content;

    private String selector = "[class='features'] h2";

    public ContentFragment content() {
        return new ContentFragment(content, selector);
    }

    public FeaturesPageVerifier verify() {
        return new FeaturesPageVerifier(driver);
    }

    public class FeaturesPageVerifier extends PageVerifier {
        public FeaturesPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
