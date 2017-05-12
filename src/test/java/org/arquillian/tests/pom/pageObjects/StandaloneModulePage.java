package org.arquillian.tests.pom.pageObjects;

import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneModulePage {

    @Drone
    private WebDriver driver;

    @FindBy(id = "content")
    private WebElement contentRoot;

    public IndividualModulePageVerifier verify() {
        return new IndividualModulePageVerifier();
    }

    public PageNavigator navigate() {
        String navigationSelector = "[class = 'row']";
        return new PageNavigator(contentRoot, navigationSelector);
    }

    public class IndividualModulePageVerifier {

        public IndividualModulePageVerifier hasTitle(String title) {
            assertThat(driver.getTitle()).isEqualTo(title);
            return this;
        }

        public IndividualModulePageVerifier hasModuleSummary() {
            assertThat(contentRoot.findElement(By.className("span9")).isDisplayed()).isTrue();
            return this;
        }

        public IndividualModulePageVerifier hasSourceRepoInfo() {
            assertThat(contentRoot.findElement(By.className("span3")).isDisplayed()).isTrue();
            return this;
        }

        public IndividualModulePageVerifier hasSections(String... expectedSections) {
            final List<WebElement> fragmentItems = contentRoot.findElements(By.tagName("section"));
            final List<String> fragmentItemsTitles =
                fragmentItems.stream().map(list -> list.getAttribute("id")).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).containsExactly(expectedSections);

            return this;
        }

        public IndividualModulePageVerifier hasDocumentation(Boolean value) {
            if (value) {
                assertThat(contentRoot.findElement(By.linkText("Documentation")).isDisplayed()).isTrue();
            }
            return this;
        }
    }
}

