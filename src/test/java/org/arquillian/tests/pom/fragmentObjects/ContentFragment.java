package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentFragment {

    @Root
    private WebElement contentRoot;

    public ContentVerifier verify() {
        return new ContentVerifier();
    }

    public class ContentVerifier {
        public ContentVerifier hasSectionsDisplayedInOrder(String... expectedFragmentItems) {
            final List<WebElement> fragmentItems = contentRoot.findElements(By.cssSelector("h2"));
            final List<String> fragmentItemsTitles =
                fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).containsExactly(expectedFragmentItems);

            return this;
        }
    }
}
