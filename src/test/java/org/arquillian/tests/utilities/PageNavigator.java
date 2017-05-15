package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class PageNavigator {

    private WebElement fragmentRoot;

    public PageNavigator(WebElement fragmentRoot) {
        this.fragmentRoot = fragmentRoot;
    }

    public void to(String fragmentItem) {
        fragmentRoot.findElement(By.partialLinkText(fragmentItem)).click();
    }
}
