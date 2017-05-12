package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.DocsPage;
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
public class DocsPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private DocsPage docsPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_content_listing_all_blogs_with_title_and_release_notes() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");
        //"//*[@class='well']//descendant::a:not(class='muted')]"
        //System.out.println(driver.findElement(ByJQuery.selector(".well a:root")).getText());
        /*docsPage.content()
            .verify()
            .containsInOrder("Core", "Algeron Extension", "Cube Extension", "Cube Q Extension", "Drone Extension",
                "Extension Performance", "Persistence Extension", "Warp", "Graphene");*/
    }
}
