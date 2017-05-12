package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class PageNavigator {

    private WebElement fragmentRoot;

    private String selector;

    public PageNavigator(WebElement fragmentRoot, String selector) {
        this.fragmentRoot = fragmentRoot;
        this.selector = selector;
    }

    public void to(String fragmentItem) {
        fragmentRoot.findElement(By.cssSelector(selector)).findElement(By.partialLinkText(fragmentItem)).click();
    }
}
