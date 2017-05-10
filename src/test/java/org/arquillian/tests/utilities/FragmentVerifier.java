package org.arquillian.tests.utilities;

import java.util.List;
import java.util.stream.Collectors;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class FragmentVerifier {

    private WebElement fragmentRoot;

    public FragmentVerifier(WebElement fragmentRoot) {
        this.fragmentRoot = fragmentRoot;
    } 
    
    public FragmentVerifier containsInOrder(String... expectedFragmentItems) {
        final List<WebElement> fragmentItems = fragmentRoot.findElement(By.cssSelector("[class='nav']"))
            .findElements(By.tagName("li"));

        final List<String> fragmentItemsTitles =
            fragmentItems.stream().map(WebElement::getText).collect(Collectors.toList());

        assertThat(fragmentItemsTitles).containsExactly(expectedFragmentItems);

        return this;
    }
}
