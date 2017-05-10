package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class FragmentNavigator {

    private WebElement fragmentRoot;

    public FragmentNavigator(WebElement fragmentRoot) {
        this.fragmentRoot = fragmentRoot;
    }

    public void to(String fragmentItem) {
        fragmentRoot.findElement(By.cssSelector("[class='nav']")).findElement(By.linkText(fragmentItem)).click();
    }
}
