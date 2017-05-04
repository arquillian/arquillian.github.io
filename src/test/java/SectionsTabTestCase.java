import java.util.HashMap;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class SectionsTabTestCase {

    @Drone
    WebDriver driver;

    HashMap<String, String> sectionMap = new HashMap<String, String>() {{
        put("http://arquillian.org/", "Arquillian · Write Real Tests");
        put("http://arquillian.org/invasion/", "Arquillian Invasion! · Arquillian");
        put("http://arquillian.org/features/", "Feature Tour · Arquillian");
        put("http://arquillian.org/guides/", "Guides · Arquillian");
        put("http://arquillian.org/docs/", "Documentation · Arquillian");
        put("http://arquillian.org/blog/", "Arquillian Blog · Arquillian");
        put("http://arquillian.org/community/", "Community · Arquillian");
    }};

    @Test
    public void test_all_sections_are_reachable() {
        for (String sectionUrl : sectionMap.keySet()) {
            String expectedPageTitle = sectionMap.get(sectionUrl);
            driver.get(sectionUrl);
            String actualPageTitle = driver.getTitle();
            assertEquals(actualPageTitle, expectedPageTitle);
        }
    }
}
