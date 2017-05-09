package arquillian.github.com.tasks;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class Verify {
    public static void hasContent(WebDriver driver, By selector) {
        List<WebElement> elements = driver.findElements(selector);
        for (WebElement element : elements) {
            String content = element.getText();
            System.out.println(content);
        }
    }
}
