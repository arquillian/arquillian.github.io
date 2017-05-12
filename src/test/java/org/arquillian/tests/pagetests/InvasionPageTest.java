package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.InvasionPage;
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
public class InvasionPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private InvasionPage invasionPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
        mainPage.menu()
            .navigate().to("Invasion!");
    }

    @Test
    public void should_have_all_sidebar_items() throws Exception {
        invasionPage.sidebar()
            .verify()
            .containsInOrder("Mission", "Team", "Origins", "Buzz", "Events", "Press", "Videos", "Spread Ike!");
    }

    @Test
    public void should_be_able_to_go_to_main_invasion_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Mission");

        invasionPage.verify().hasTitle("Arquillian Invasion! · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_team_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Team");

        invasionPage.verify().hasTitle("Team · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_origins_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Origins");

        invasionPage.verify().hasTitle("Origins · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_buzz_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Buzz");

        invasionPage.verify().hasTitle("Transmissions · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_events_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Events");

        invasionPage.verify().hasTitle("Events · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_press_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Press");

        invasionPage.verify().hasTitle("Press · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_videos_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Videos");

        invasionPage.verify().hasTitle("Videos · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_spread_ike_sub_page() throws Exception {
        invasionPage.sidebar()
            .navigate().to("Spread Ike!");

        invasionPage.verify().hasTitle("Spread Ike! · Arquillian")
            .hasContent();
    }
}
