package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.PageVerifier;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.WebDriver;

public class BlogPage {
    
    @Drone
    private WebDriver driver;

    public BlogPageVerifier verify() {
        return new BlogPageVerifier(driver);
    }

    public class BlogPageVerifier extends PageVerifier {
        public BlogPageVerifier(WebDriver driver) {
            super(driver);
        }
    }
}
