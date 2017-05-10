package org.arquillian.tests.pom;

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
        public ContentVerifier containsInOrder(String... expectedContentItems) {
            final List<WebElement> contentItems = contentRoot.findElements(By.cssSelector("[class='features'] h2"));
            final List<String> contentItemsHeaders =
                contentItems.stream().map(WebElement::getText).collect(Collectors.toList());

            assertThat(contentItemsHeaders).size().isEqualTo(9);
            assertThat(contentItemsHeaders).containsExactly(expectedContentItems);

            return this;
        }
    }
}
