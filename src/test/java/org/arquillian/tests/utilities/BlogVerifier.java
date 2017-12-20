package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BlogVerifier {

    public static void hasTitle(WebElement blog) {
        WebElement blogTitle = getBlogTitle(blog);
        assertThat(blogTitle.isDisplayed()).isTrue();
    }

    public static void hasReleaseNotes(WebElement item) {
        if (hasTypeRelease(item)) {
            try {
                WebElement releaseNoteTitle =
                        item.findElement(By.xpath(".//h3[contains(text(),'Release notes and resolved issues')]"));
                List<WebElement> releaseNoteContents = item.findElements(By.xpath(".//dl"));

                assertThat(releaseNoteTitle.isDisplayed() && releaseNoteContents.stream()
                        .allMatch(WebElement::isDisplayed)).isTrue();

            } catch (NoSuchElementException e) {
                throw new NoSuchElementException(
                        "Missing release notes for blog post titled: " + getBlogTitle(item).getText() + ".\n" +
                                "If the release was performed manually, this happen because we forgot to: \n" +
                                "- close the milestone on GitHub or release version on JIRA\n" +
                                "- push tag to the upstream repo after releasing to Maven Central (git push origin --tags)");
            }
        }
    }

    public static void haveAnnouncementBanner(WebElement item) {
        try {
            WebElement announcementBanner = item.findElement(By.partialLinkText("Check our latest announcement"));
            assertThat(announcementBanner.isDisplayed()).isTrue();
        } catch (NoSuchElementException e) {
            // Ignore if no announcement banner
        }
    }

    public static void doesNotHaveAnnouncementBanner(WebElement item) {
        WebElement announcementBanner = null;
        try {
            announcementBanner = item.findElement(By.partialLinkText("Check our latest announcement"));
            assertThat(announcementBanner.isDisplayed()).isFalse();
        } catch (NoSuchElementException e) {
            assertThat(announcementBanner).isNull();
        }
    }

    private static boolean hasTypeRelease(WebElement item) {
        try {
            item.findElement(By.linkText("release"));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private static WebElement getBlogTitle(WebElement blog) {
        return blog.findElement(By.cssSelector("[class='title'] a"));
    }
}


