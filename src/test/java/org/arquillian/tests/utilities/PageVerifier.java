package org.arquillian.tests.utilities;

import java.util.function.Function;
import org.jboss.arquillian.graphene.Graphene;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class PageVerifier {

    private final WebDriver driver;

    public PageVerifier(WebDriver driver) {
        this.driver = driver;
    }

    public PageVerifier hasTitle(String title) {
        Graphene.waitModel()
            .withMessage(String.format("The expected title is [%s] but was [%s]", title, driver.getTitle()))
            .until((Function<WebDriver, Boolean>)webDriver -> title.equals(driver.getTitle()));
        return this;
    }

    public PageVerifier hasContent() {
        assertThat(driver.findElement(By.id("content")).isDisplayed()).isTrue();
        return this;
    }
}
