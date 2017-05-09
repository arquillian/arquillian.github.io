package arquillian.github.com.tests;

import arquillian.github.com.tasks.Redirect;
import arquillian.github.com.tasks.Verify;
import arquillian.github.com.webpages.ArquillianHomePage;
import arquillian.github.com.webpages.BlogPage;
import arquillian.github.com.webpages.CommunityPage;
import arquillian.github.com.webpages.DocsPage;
import arquillian.github.com.webpages.FeaturePage;
import arquillian.github.com.webpages.GuidesPage;
import arquillian.github.com.webpages.InvasionPage;
import arquillian.github.com.webpages.ModulesPage;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

@RunWith(Arquillian.class)
public class ArquillianHomePageTest {

    @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    @Before
    public void setup() {
        driver.get(ARQUILLIAN_URL);
    }

    @Test
    public void verify_section_tabs_on_navigation_bar_are_reachable() {
        Redirect.to(driver, ArquillianHomePage.invasionTab, InvasionPage.invasionPage);
        Redirect.to(driver, ArquillianHomePage.featuresTab, FeaturePage.featurePage);
        Redirect.to(driver, ArquillianHomePage.guidesTab, GuidesPage.guidesPage);
        Redirect.to(driver, ArquillianHomePage.docsTab, DocsPage.docsPage);
        Redirect.to(driver, ArquillianHomePage.blogTab, BlogPage.blogPage);
        Redirect.to(driver, ArquillianHomePage.communityTab, CommunityPage.communityPage);
        Redirect.to(driver, ArquillianHomePage.modulesTab, ModulesPage.modulesPage);
    }

    @Test
    public void verify_getting_started_button_redirects_to_new_page() {
        By gettingStartedGuidePagePath = By.xpath("//*[@id='content-header']/div/div/h1/a");
        Redirect.to(driver, ArquillianHomePage.getStartedButton, gettingStartedGuidePagePath);
    }

    @Test
    public void verify_blog_posts_are_visible_on_landing_page() {
        Verify.hasContent(driver, ArquillianHomePage.blogPostList);
    }
}

