package org.arquillian.tests.utilities;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class PageVerifier {

    private final WebDriver driver;

    public PageVerifier(WebDriver driver) {
        this.driver = driver;
    }

    public PageVerifier hasTitle(String title) {
        assertThat(driver.getTitle()).isEqualTo(title);
        return this;
    }

    public PageVerifier hasContent() {
        assertThat(driver.findElement(By.id("content")).isDisplayed()).isTrue();
        return this;
    }

}
