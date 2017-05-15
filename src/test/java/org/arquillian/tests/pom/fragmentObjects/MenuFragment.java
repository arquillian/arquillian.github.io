package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class MenuFragment {

    @Root
    private WebElement menuRoot;

    public MenuVerifier verify() {
        return new MenuVerifier();
    }

    public PageNavigator navigate() {
        return new PageNavigator(menuRoot);
    }

    public class MenuVerifier {
        public MenuVerifier hasMenuItemsDisplayedInOrder(String... expectedFragmentItems) {
            final List<WebElement> fragmentItems = menuRoot.findElements(By.cssSelector("li"));
            final List<String> fragmentItemsTitles =
                fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).containsExactly(expectedFragmentItems);

            return this;
        }
    }
}
