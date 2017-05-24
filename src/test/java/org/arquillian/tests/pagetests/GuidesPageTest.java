package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.GuidesPage;
import org.arquillian.tests.pom.pageObjects.MainPage;
import org.arquillian.tests.pom.pageObjects.StandalonePage;
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
public class GuidesPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private GuidesPage guidesPage;

    @Page
    private StandalonePage fetchedGuidePage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_a_listing_of_guides() throws Exception {
        mainPage.menu()
            .navigate().to("Guides");

        guidesPage.descriptionList()
            .verify()
            .containsEntries("Getting Started: Rinse and Repeat", "Functional Testing using Drone and Graphene",
                "Get Started Faster with Forge", "Reference Guide", "FAQs");
    }

    @Test
    public void should_be_able_to_go_to_selected_guide_page() throws Exception {
        mainPage.menu()
            .navigate().to("Guides");

        guidesPage.descriptionList()
            .navigate().to("Getting Started: Rinse and Repeat");

        fetchedGuidePage.verify()
            .hasTitle("Getting Started: Rinse and Repeat · Arquillian Guides")
            .hasContent();
    }

    @Test
    public void should_have_sidebar_with_items() throws Exception {
        mainPage.menu()
            .navigate().to("Guides");

        guidesPage.sidebar()
            .verify()
            .hasSubSectionHeaders("ICON LEGEND", "CONVENTIONS");
    }
}
