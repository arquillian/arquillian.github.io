package org.arquillian.tests.pom.fragmentObjects;

import java.util.List;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class BlogFragment {

    @Root
    private WebElement contentRoot;

    public BlogVerifier verify() {
        return new BlogVerifier();
    }

    public class BlogVerifier {

        final List<WebElement> fragmentItems = contentRoot.findElements(By.cssSelector("[class='post']"));

        public BlogVerifier hasTitle() {
            for (WebElement blog : fragmentItems) {
                WebElement blogTitle = getBlogTitle(blog);
                assertThat(blogTitle.isDisplayed()).isTrue();
            }
            return this;
        }

        public BlogVerifier hasReleaseNotes() {
            for (WebElement item : fragmentItems) {
                try {
                    WebElement releaseNoteTitle =
                        item.findElement(By.xpath(".//h3[contains(text(),'Release notes and resolved issues')]"));
                    List<WebElement> releaseNoteContents = item.findElements(By.xpath(".//dl"));

                    assertThat(releaseNoteTitle.isDisplayed() && releaseNoteContents.stream()
                        .allMatch(WebElement::isDisplayed)).isTrue();

                } catch (NoSuchElementException e) {
                    throw new NoSuchElementException(
                        "Missing release notes for blog post titled: " + getBlogTitle(item).getText());
                }
            }
            return this;
        }

        private WebElement getBlogTitle(WebElement blog) {
            return blog.findElement(By.cssSelector("[class='title'] a"));
        }
    }

    public PageNavigator navigate() {
        return new PageNavigator(contentRoot);
    }
}
