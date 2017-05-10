package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.FeaturesPage;
import org.arquillian.tests.pom.MainPage;
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
        mainPage.menu()
            .navigate().to("Features");
    }

    @Test
    public void should_have_sidebar_with_all_items() throws Exception {
        featuresPage.content()
            .verify()
            .containsInOrder("Real Tests", "IDE Friendly", "Test Enrichment", "Classpath Control", "Drive the Browser",
                "Debug the Server", "Container Agnostic", "Extensible Platform", "Strong Tooling");
    }

    //todo fix bug - misplaced divs in strong tooling
}
