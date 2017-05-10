package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.BlogPage;
import org.arquillian.tests.pom.CommunityPage;
import org.arquillian.tests.pom.DocsPage;
import org.arquillian.tests.pom.FeaturesPage;
import org.arquillian.tests.pom.GuidesPage;
import org.arquillian.tests.pom.InvasionPage;
import org.arquillian.tests.pom.MainPage;
import org.arquillian.tests.pom.ModulesPage;
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


    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_all_menu_items() throws Exception {
        mainPage.menu()
            .verify()
            .containsInOrder("Invasion!", "Features", "Guides", "Docs", "Blog", "Community", "Modules");
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
}
