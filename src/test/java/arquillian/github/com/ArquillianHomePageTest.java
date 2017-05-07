package arquillian.github.com;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
public class ArquillianHomePageTest {

    @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    private String expectedPageUrl;
    private String fetchedPageUrl;
    private String fetchedPageTitle;

    @FindBy(linkText = "Invasion!")
    private WebElement invasionTab;

    @FindBy(linkText = "Features")
    private WebElement featuresTab;

    @FindBy(linkText = "Guides")
    private WebElement guidesTab;

    @FindBy(linkText = "Docs")
    private WebElement docsTab;

    @FindBy(linkText = "Blog")
    private WebElement blogTab;

    @FindBy(linkText = "Community")
    private WebElement communityTab;

    @FindBy(linkText = "Modules")
    private WebElement modulesTab;

    @FindBy(linkText = "Get Started!")
    private WebElement getStartedButton;

    @Before
    public void fetchHomePage() {
        driver.get(ARQUILLIAN_URL);
    }

    @Test
    public void verify_invasion_tab_on_click_is_reachable() {
        expectedPageUrl = invasionTab.getAttribute("href");

        invasionTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Arquillian Invasion! · Arquillian");
    }

    @Test
    public void verify_features_tab_on_click_is_reachable() {
        expectedPageUrl = featuresTab.getAttribute("href");

        featuresTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Feature Tour · Arquillian");
    }

    @Test
    public void verify_guides_tab_on_click_is_reachable() {
        expectedPageUrl = guidesTab.getAttribute("href");

        guidesTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Guides · Arquillian");
    }

    @Test
    public void verify_docs_tab_on_click_is_reachable() {
        expectedPageUrl = docsTab.getAttribute("href");

        docsTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Documentation · Arquillian");
    }

    @Test
    public void verify_blog_tab_on_click_is_reachable() {
        expectedPageUrl = blogTab.getAttribute("href");

        blogTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Arquillian Blog · Arquillian");
    }

    @Test
    public void verify_community_tab_on_click_is_reachable() {
        expectedPageUrl = communityTab.getAttribute("href");

        communityTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Community · Arquillian");
    }

    @Test
    public void verify_modules_tab_on_click_is_reachable() {
        expectedPageUrl = modulesTab.getAttribute("href");

        modulesTab.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Modules · Arquillian");
    }

    @Test
    public void verify_get_started_button_on_click_is_reachable() {
        expectedPageUrl = getStartedButton.getAttribute("href");

        getStartedButton.click();
        fetchedPageUrl = driver.getCurrentUrl();
        fetchedPageTitle = driver.getTitle();

        assertThat(fetchedPageUrl).isEqualTo(expectedPageUrl);
        assertThat(fetchedPageTitle).isEqualTo("Getting Started · Arquillian Guides");
    }
}
