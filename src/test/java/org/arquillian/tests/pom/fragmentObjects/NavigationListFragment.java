package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class NavigationListFragment {

    @Root
    private WebElement navListRoot;

    private String selector;

    private String navigationSelector;

    public NavigationListFragment(WebElement navListRoot, String selector, String navigationSelector) {
        this.navListRoot = navListRoot;
        this.selector = selector;
        this.navigationSelector = navigationSelector;
    }

    public NavigationListVerifier verify() {
        return new NavigationListVerifier();
    }

    public PageNavigator navigate() {
        return new PageNavigator(navListRoot, navigationSelector);
    }

    public class NavigationListVerifier {

        public NavigationListVerifier containsEntries(String... expectedItems) {
            final List<WebElement> fragmentItems = navListRoot.findElements(By.cssSelector(selector));
            List<String> fragmentItemsTitles =
                fragmentItems.stream().map(list -> formatTitle(list.getText())).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).contains(expectedItems);

            return this;
        }

        private String formatTitle(String title) {
            String version = "SNAPSHOT";
            if (title.contains(version)) {
                String[] titleArray = title.split(version);
                return titleArray[0].trim();
            }
            else if(title.contains("–")) {
                return title.substring(0, (title.indexOf("–") - 1));
            }
            return title;
        }
    }
}
