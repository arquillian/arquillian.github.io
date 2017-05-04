import java.util.HashMap;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.junit.Assert.assertEquals;

//@RunWith(value = Parameterized.class)
@RunWith(Arquillian.class)
public class DocSectionExtensionListTestCase {

    @Drone
    WebDriver driver;

    HashMap<String, String> extensionMap = new HashMap<String, String>() {{
        put("Core", "http://arquillian.org/arquillian-core/");
        put("Algeron", "http://arquillian.org/arquillian-algeron/");
        put("Cube", "http://arquillian.org/arquillian-cube/");
        put("Cube Q", "http://arquillian.org/arquillian-cube-q/");
        put("Drone", "http://arquillian.org/arquillian-extension-drone/");
        put("Performance", "http://arquillian.org/arquillian-extension-performance/");
        put("Persistence", "http://arquillian.org/arquillian-extension-persistence/");
        put("Warp", "http://arquillian.org/arquillian-extension-warp/");
        put("Graphene", "http://arquillian.org/arquillian-graphene/");
    }};

    @Test
    public void test_extension_links_in_docs_section_are_reachable() {
        for (String extension : extensionMap.keySet()) {
            driver.get("http://arquillian.org/docs/");
            String extensionUrl = findExtensionElement(driver, extension);
            verifyExtensionLink(extensionUrl, extensionMap.get(extension));
        }
    }

    private String findExtensionElement(WebDriver driver, String extension) {
        driver.findElement(By.partialLinkText(extension)).click();
        String currentUrl = driver.getCurrentUrl();
        return currentUrl;
    }

    private void verifyExtensionLink(String actualExtensionUrl, String expectedExtensionUrl) {
        assertEquals(actualExtensionUrl, expectedExtensionUrl);
    }

  /*  private String  extensionName;
    private String extensionURL;

    public DocSectionExtensionListTestCase(String extensionName, String extensionURL) {
        this.extensionName = extensionName;
        this.extensionURL = extensionURL;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"Algeron", "http://arquillian.org/arquillian-algeron/"},
            {"Cube", "http://arquillian.org/arquillian-cube/" },
            {"Cube Q", "http://arquillian.org/arquillian-cube-q/" },
            {"Drone", "http://arquillian.org/arquillian-extension-drone/"},
            {"Performance", "http://arquillian.org/arquillian-extension-performance/" },
            {"Persistence", "http://arquillian.org/arquillian-extension-persistence/" },
            {"Warp", "http://arquillian.org/arquillian-extension-warp/" },
            {"Graphene", "http://arquillian.org/arquillian-graphene/" }
        });
    }

    @Test
    public void test_extension_links_in_docs_section_are_reachable() {
        driver.get("http://arquillian.org/docs/");
        String actualExtensionUrl = findExtension(driver, extensionName);
        Assert.assertEquals(actualExtensionUrl, extensionURL);
    }
*/
}
