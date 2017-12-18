package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.BlogPage;
import org.arquillian.tests.pom.pageObjects.MainPage;
import org.arquillian.tests.pom.pageObjects.StandalonePage;
import org.arquillian.tests.utilities.ArquillianBlogInstance;
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

    @Page
    private StandalonePage fetchedBlogPage;

    @Before
    public void open() {
        driver.navigate().to(ArquillianBlogInstance.getUrl());
    }

    @Test
    public void should_have_content_listing_all_release_blogs_with_title_and_release_notes() throws Exception {

        mainPage.menu()
            .navigate().to("Blog");

        blogPage.releaseBlog()
            .verify()
                .hasTitle()
                .hasReleaseNotes();
    }

    @Test
    public void should_have_sidebar_with_sub_sections() throws Exception {
        mainPage.menu()
            .navigate().to("Blog");

        blogPage.sidebar()
            .verify()
                .hasSubSectionHeaders("Subscribe to the Arquillian Blog", "Latest Posts", "Popular Posts", "Tags");
    }

    @Test
    public void should_be_able_to_go_to_jacoco_blog_from_cloud_tag() {
        mainPage.menu()
            .navigate().to("Blog");

        blogPage.cloudTag()
            .navigate().to("jacoco");

        fetchedBlogPage.verify().hasTitle("Arquillian Blog · Arquillian")
            .hasContent();

        blogPage.releaseBlog()
            .verify()
                .hasTitle()
                .hasReleaseNotes();
    }

    @Test
    public void should_redirect_to_new_announcement_if_banner_is_present() throws Exception {
        mainPage.menu()
            .navigate().to("Blog");

        blogPage.cloudTag()
            .navigate().to("drone");

        blogPage.releaseBlog()
            .verify()
                .hasAnnouncementBanner(true);

        blogPage.newAnnouncementBanner()
            .navigate().to("Check our latest announcement");

        blogPage.releaseBlog()
            .verify()
                .hasAnnouncementBanner(false);
    }

    @Test
    public void should_ignore_release_notes_for_non_release_blog_posts() throws Exception {
        mainPage.menu()
                .navigate().to("Blog");

        blogPage.cloudTag()
                .navigate().to("nonrelease");

        blogPage.nonReleaseBlog()
                .verify()
                    .hasTitle();
    }
}
