package arquillian.github.com;

import java.util.List;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.arquillian.graphene.Graphene.waitGui;

@RunWith(Arquillian.class)
public class DocsPageTest {

    @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    private String expectedDocURL;
    private String fetchedDocURL;
    private String fetchedDocTitle;

    @FindBy(linkText = "Docs")
    private WebElement docsTab;

    @FindByJQuery("a:contains('Core')")
    private WebElement arquillianCore;

    @FindByJQuery("a:contains('Algeron Extension')")
    private WebElement algeronExtension;

    @FindByJQuery("a:contains('Cube Extension')")
    private WebElement cubeExtension;

    @FindByJQuery("a:contains('Cube Q Extension')")
    private WebElement cubeQExtension;

    @FindByJQuery("a:contains('Drone Extension')")
    private WebElement droneExtension;

    @FindByJQuery("a:contains('Extension Performance')")
    private WebElement performanceExtension;

    @FindByJQuery("a:contains('Persistence Extension')")
    private WebElement persistenceExtension;

    @FindByJQuery("a:contains('Warp')")
    private WebElement warpExtension;

    @FindByJQuery("a:contains('Graphene')")
    private WebElement grapheneExtension;

    @FindBy(xpath = "//*[@data-toggle='tab']")
    private List<WebElement> coreConceptTabList;

    @Before
    public void ensure_successful_loading_of_docs_page() {
        driver.get(ARQUILLIAN_URL);
        docsTab.click();
        waitGui().until().element(By.linkText("Documentation")).is().present();
    }

    @Test
    public void verify_arquillian_core_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(arquillianCore);

        arquillianCore.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian - So you can rule your code. Not the bugs.");
    }

    @Test
    public void verify_algeron_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(algeronExtension);

        algeronExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Algeron");
    }

    @Test
    public void verify_cube_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(cubeExtension);

        cubeExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Cube");
    }

    @Test
    public void verify_cubeQ_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(cubeQExtension);

        cubeQExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Introduction");
    }

    @Test
    public void verify_drone_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(droneExtension);

        droneExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Drone");
    }

    @Test
    public void verify_performance_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(performanceExtension);

        performanceExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Performance Extension");
    }

    @Test
    public void verify_persistence_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(persistenceExtension);

        persistenceExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Persistence Extension");
    }

    @Test
    public void verify_warp_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(warpExtension);

        warpExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Warp");
    }

    @Test
    public void verify_graphene_doc_link_on_click_is_reachable() {
        expectedDocURL = getDocUrlFor(grapheneExtension);

        grapheneExtension.click();
        fetchedDocURL = driver.getCurrentUrl();
        fetchedDocTitle = driver.getTitle();

        assertThat(fetchedDocURL).isEqualTo(expectedDocURL);
        assertThat(fetchedDocTitle).isEqualTo("Arquillian Graphene 2");
    }

    @Test
    public void verify_core_concept_tab_toggles_content_on_click() throws InterruptedException {
        for (WebElement tabPane : coreConceptTabList) {
            String tabPaneID = getTabPaneId(tabPane.getText());

            tabPane.sendKeys("");
            waitGui().until().element(tabPane).is().visible();
            tabPane.click();
            WebElement tabPaneContent = driver.findElement(By.id(tabPaneID));

            assertThat(tabPaneContent.isDisplayed()).isTrue();
        }
    }

    private String getDocUrlFor(WebElement extension) {
        return extension.getAttribute("href") + "/";
    }

    private String getTabPaneId(String tabPane) {
        return tabPane.toLowerCase().replace(" ", "-");
    }
}
