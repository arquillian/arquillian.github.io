package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class FeaturesPage {

    @Drone
    private WebDriver driver;

    @FindBy(className = "features")
    private ContentFragment content;

    public ContentFragment content() {
        return content;
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
