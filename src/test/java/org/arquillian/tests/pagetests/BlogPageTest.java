package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.BlogPage;
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
public class BlogPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private BlogPage blogPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_content_listing_all_blogs_with_title_and_release_notes() throws Exception {

        mainPage.menu()
            .navigate().to("Blog");

        blogPage.blogs()
            .verify()
                .hasTitle()
                .hasReleaseNotes();
    }

    //fixme bug - missing release notes for blogs

    @Test
    public void should_have_sidebar_with_items() throws Exception {
        mainPage.menu()
            .navigate().to("Blog");

        blogPage.sidebar()
            .verify()
            .containsInOrder("Subscribe to the Arquillian Blog", "Latest Posts", "Popular Posts", "Tags");
    }
}
