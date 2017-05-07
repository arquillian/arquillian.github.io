package arquillian.github.com;

import java.util.List;
import org.jboss.arquillian.drone.api.annotation.Drone;
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
public class ModulesPageTest {

    @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    @FindBy(linkText = "Modules")
    private WebElement modulesTab;

    @FindBy(xpath = "//*[@class='well']//descendant::a")
    private List<WebElement> moduleList;

    @Before
    public void ensure_successful_loading_of_modules_page() {
        driver.get(ARQUILLIAN_URL);
        modulesTab.click();
        waitGui().until().element(By.linkText("Modules")).is().present();
    }

    @Test
    public void verify_modules_are_listed_on_landing_page_and_are_reachable() {
        assertThat(moduleList).size().isPositive();

        for (WebElement module : moduleList) {
            ensure_successful_loading_of_modules_page();
            String expectedModuleTitle = formatTitle(module.getText());
            String expectedModuleURL = module.getAttribute("href");
         
            module.click();

            String actualModuleTitle = driver.getTitle();
            String actualModuleURL = driver.getCurrentUrl();

            assertThat(actualModuleURL).isEqualTo(expectedModuleURL);
            assertThat(actualModuleTitle).contains(expectedModuleTitle);
        }
    }

    private String formatTitle(String title) {
        String version = "SNAPSHOT";
        if (title.contains(version)) {
            String[] titleArray = title.split(version);
            return titleArray[0];
        }
        return title.substring(0, title.indexOf("–"));
    }
}