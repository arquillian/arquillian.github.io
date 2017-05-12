package org.arquillian.tests.pom;

import java.util.List;
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
                WebElement blogTitle = blog.findElement(By.cssSelector("[class='title'] a"));
                assertThat(blogTitle.isDisplayed()).isTrue();
            }
            return this;
        }

        public BlogVerifier hasReleaseNotes() {

            for (WebElement item : fragmentItems) {
                try {

                    WebElement releaseNoteTitle =
                        item.findElement(By.xpath(".//h3[contains(text(),'Release notes and resolved issues')]"));

                    //System.out.println(releaseNoteTitle.getText());

                    List<WebElement> releaseNoteContents = item.findElements(By.xpath(".//dl"));

                    //todo add sections with id in html for each heading

                    /*String content =
                        releaseNoteContents.stream().map(WebElement::getText).collect(Collectors.joining("\n"));

                    System.out.println(content);*/

                    assertThat(releaseNoteTitle.isDisplayed() && releaseNoteContents.stream()
                        .allMatch(WebElement::isDisplayed)).isTrue();

                } catch (NoSuchElementException exception) {
                    System.out.println(
                        "Release notes missing for blog post titled: " + item.findElement(By.cssSelector("[class='title']"))
                            .getText());
                    exception.printStackTrace();
                }
            }
            return this;
        }
    }
}
