package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class SideBarFragment {

    @Root
    private WebElement sidebarRoot;

    public SideBarVerifier verify() {
        return new SideBarVerifier();
    }

    public PageNavigator navigate() {
        return new PageNavigator(sidebarRoot);
    }

    public class SideBarVerifier {
        public SideBarVerifier hasLinkableSubSections(String... expectedFragmentItems) {
            final List<WebElement> fragmentItems = sidebarRoot
                .findElement(By.cssSelector("[class='nav']")).findElements(By.cssSelector("li"));
            verifySideBarItems(fragmentItems, expectedFragmentItems);
            return this;
        }

        public SideBarVerifier hasSubSectionHeaders(String... expectedFragmentItems) {
            final List<WebElement> fragmentItems = sidebarRoot.findElements(By.cssSelector("h2,h3"));
            verifySideBarItems(fragmentItems, expectedFragmentItems);
            return this;
        }

        private void verifySideBarItems(List<WebElement> fragmentItems, String[] expectedFragmentItems) {
            final List<String> fragmentItemsTitles =
                fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).containsExactly(expectedFragmentItems);
        }
    }
}
