package arquillian.github.com.tasks;

import arquillian.github.com.actions.Click;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class Redirect {

    static String fetchedPageHeader;

    public static void to(WebDriver driver, By sourceSelector, By destinationSelector) {
        Click.on(driver, sourceSelector);
        fetchedPageHeader = driver.findElement(destinationSelector).getText();
        System.out.println(fetchedPageHeader);
    }
}
