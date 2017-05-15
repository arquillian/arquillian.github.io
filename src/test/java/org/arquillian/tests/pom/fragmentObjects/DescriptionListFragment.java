package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class DescriptionListFragment {
    @Root
    private WebElement descListRoot;

    public DescriptionListVerifier verify() {
        return new DescriptionListVerifier();
    }

    public class DescriptionListVerifier {

        public DescriptionListVerifier containsEntries(String... expectedItems) {
            final List<WebElement> fragmentItems = descListRoot.findElements(By.cssSelector(" dl a"));
            List<String> fragmentItemsTitles =
                fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).contains(expectedItems);

            return this;
        }
    }

    public PageNavigator navigate() {
        return new PageNavigator(descListRoot);
    }
}