package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.jboss.arquillian.graphene.Graphene.waitGui;

public class PageNavigator {

    private WebElement fragmentRoot;

    public PageNavigator(WebElement fragmentRoot) {
        this.fragmentRoot = fragmentRoot;
    }

    public void to(String fragmentItem) {
        waitGui().until().element(By.partialLinkText(fragmentItem)).is().present();
        fragmentRoot.findElement(By.partialLinkText(fragmentItem)).click();
    }
}
