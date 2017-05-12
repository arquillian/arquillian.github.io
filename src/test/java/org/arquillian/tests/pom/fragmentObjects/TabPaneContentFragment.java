package org.arquillian.tests.pom.fragmentObjects;

import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class TabPaneContentFragment {
    @Root
    private WebElement contentRoot;

    public TabPaneContentVerifier verify() {
        return new TabPaneContentVerifier();
    }

    public class TabPaneContentVerifier {
        public TabPaneContentVerifier containsDescForItem(String expectedItem) {
            String itemID = expectedItem.toLowerCase().replace(" ", "-");

            final WebElement fragmentItem = contentRoot.findElement(By.id(itemID));
            assertThat(fragmentItem.isDisplayed()).isTrue();

            return this;
        }
    }
}
