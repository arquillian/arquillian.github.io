package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.BlogPage;
import org.arquillian.tests.pom.pageObjects.CommunityPage;
import org.arquillian.tests.pom.pageObjects.DocsPage;
import org.arquillian.tests.pom.pageObjects.FeaturesPage;
import org.arquillian.tests.pom.pageObjects.GuidesPage;
import org.arquillian.tests.pom.pageObjects.InvasionPage;
import org.arquillian.tests.pom.pageObjects.MainPage;
import org.arquillian.tests.pom.pageObjects.ModulesPage;
import org.arquillian.tests.pom.pageObjects.StandalonePage;
import org.arquillian.tests.utilities.ArquillianBlogInstance;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

@RunWith(Arquillian.class)
@RunAsClient
public class MainPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private InvasionPage invasionPage;

    @Page
    private FeaturesPage featuresPage;

    @Page
    private GuidesPage guidesPage;

    @Page
    private DocsPage docsPage;

    @Page
    private BlogPage blogPage;

    @Page
    private CommunityPage communityPage;

    @Page
    private ModulesPage modulesPage;

    @Page
    private StandalonePage fetchedGuidePage;

    @Before
    public void open() {
        driver.navigate().to(ArquillianBlogInstance.getUrl());
    }

    @Test
    public void should_have_all_menu_items() throws Exception {
        mainPage.menu()
            .verify()
            .hasMenuItemsDisplayedInOrder("Invasion!", "Features", "Guides", "Docs", "Blog", "Community", "Modules");
    }

    @Test
    public void should_be_able_to_go_to_invasion_page() throws Exception {
        mainPage.menu()
            .navigate().to("Invasion!");

        invasionPage.verify().hasTitle("Arquillian Invasion! · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_features_page() throws Exception {
        mainPage.menu()
            .navigate().to("Features");

        featuresPage.verify().hasTitle("Feature Tour · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_guides_page() throws Exception {
        mainPage.menu()
            .navigate().to("Guides");

        guidesPage.verify().hasTitle("Guides · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_docs_page() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.verify().hasTitle("Documentation · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_blog_page() throws Exception {
        mainPage.menu()
            .navigate().to("Blog");

        blogPage.verify().hasTitle("Arquillian Blog · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_community_page() throws Exception {
        mainPage.menu()
            .navigate().to("Community");

        communityPage.verify().hasTitle("Community · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_modules_page() throws Exception {
        mainPage.menu()
            .navigate().to("Modules");

        modulesPage.verify().hasTitle("Modules · Arquillian")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_getting_started_page() throws Exception {
        mainPage.button()
            .navigate().to("Get Started!");

        fetchedGuidePage.verify()
            .hasTitle("Getting Started · Arquillian Guides")
            .hasContent();
    }

    @Test
    public void should_have_contributor_section_with_author_title_and_content() throws Exception {
        mainPage.content()
            .verify()
            .hasSectionsDisplayedInOrder("Contributor Spotlight", "Latest News", "Upcoming Events");
    }

    @Test
    public void should_have_section_contributor_spotlight_with_one_author_entry() throws Exception {
        mainPage.content()
            .verify()
            .hasSectionTitled("Contributor Spotlight")
            .hasNumberOfSectionEntries("Contributor Spotlight", 1);
    }

    @Test
    public void should_have_section_latest_news_with_three_blog_entries() throws Exception {
        mainPage.content()
            .verify()
            .hasSectionTitled("Latest News")
            .hasNumberOfSectionEntries("Latest News", 3);
    }

    @Test
    @Ignore("The number of events may change during time")
    public void should_have_section_upcoming_events_with_two_event_entries() throws Exception {
        mainPage.content()
            .verify()
            .hasSectionTitled("Upcoming Events")
            .hasNumberOfSectionEntries("Upcoming Events", 1);
    }
}
