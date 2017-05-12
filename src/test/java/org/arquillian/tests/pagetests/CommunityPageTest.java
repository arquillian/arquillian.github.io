package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.CommunityPage;
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
public class CommunityPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private CommunityPage communityPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_content_listing_all_sections() throws Exception {
        mainPage.menu()
            .navigate().to("Community");

        communityPage.content()
            .verify()
            .containsInOrder("Forums and Wiki", "Chat (IRC)", "Issue Tracker", "Source Repository", "Arquillian Nobles");
    }
}
