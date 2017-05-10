package org.arquillian.tests.utilities;

import java.util.List;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class FragmentVerifier {

    private WebElement fragmentRoot;

    private String selector;

    public FragmentVerifier(WebElement fragmentRoot, String selector) {
        this.fragmentRoot = fragmentRoot;
        this.selector = selector;
    }

    public FragmentVerifier containsInOrder(String... expectedFragmentItems) {
        final List<WebElement> fragmentItems = fragmentRoot.findElements(By.cssSelector(selector));
        final List<String> fragmentItemsTitles =
            fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

        assertThat(fragmentItemsTitles).containsExactly(expectedFragmentItems);

        return this;
    }
}
