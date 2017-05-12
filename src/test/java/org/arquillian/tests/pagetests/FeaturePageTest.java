package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.FeaturesPage;
import org.arquillian.tests.pom.pageObjects.MainPage;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

@RunWith(Arquillian.class)
@RunAsClient
public class FeaturePageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private FeaturesPage featuresPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_content_listing_all_features() throws Exception {
        mainPage.menu()
            .navigate().to("Features");

        featuresPage.content()
            .verify()
            .containsInOrder("Real Tests", "IDE Friendly", "Test Enrichment", "Classpath Control", "Drive the Browser",
                "Debug the Server", "Container Agnostic", "Extensible Platform", "Strong Tooling");
    }

    //fixme bug - misplaced divs in strong tooling
}
