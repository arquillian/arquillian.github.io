package org.arquillian.tests.utilities;

import org.jboss.arquillian.graphene.Graphene;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class PageNavigator {

    private WebElement fragmentRoot;

    public PageNavigator(WebElement fragmentRoot) {
        this.fragmentRoot = fragmentRoot;
    }

    public void to(String fragmentItem) {
        By bySelector = By.partialLinkText(fragmentItem);
        Graphene.waitModel().until().element(bySelector).is().visible();
        Graphene.guardHttp(fragmentRoot.findElement(bySelector)).click();
    }

    public void select(String fragmentItem) {
        By bySelector = By.partialLinkText(fragmentItem);
        Graphene.waitModel().until().element(bySelector).is().visible();
        fragmentRoot.findElement(bySelector).click();
    }
}
