package org.arquillian.tests.utilities;

import java.util.concurrent.TimeUnit;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.arquillian.graphene.Graphene.waitGui;

public class PageVerifier {

    private final WebDriver driver;

    public PageVerifier(WebDriver driver) {
        this.driver = driver;
    }

    public PageVerifier hasTitle(String title) {
        waitGui().withTimeout(10, TimeUnit.SECONDS);
        assertThat(driver.getTitle()).isEqualTo(title);
        return this;
    }

    public PageVerifier hasContent() {
        waitGui().until().element(By.id("content")).is().visible();
        assertThat(driver.findElement(By.id("content")).isDisplayed()).isTrue();
        return this;
    }
}
