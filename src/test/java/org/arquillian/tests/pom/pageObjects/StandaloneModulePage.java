package org.arquillian.tests.pom.pageObjects;

import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.Graphene;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneModulePage {

    @Drone
    private WebDriver driver;

    @FindBy(css = "[id = 'content']")
    private WebElement contentRoot;

    public IndividualModulePageVerifier verify() {
        return new IndividualModulePageVerifier();
    }

    public PageNavigator navigate() {
        return new PageNavigator(contentRoot);
    }

    public class IndividualModulePageVerifier {

        public IndividualModulePageVerifier hasTitle(String title) {
            Graphene.waitModel()
                    .withMessage(String.format("The expected title is [%s] but was [%s]", title, driver.getTitle()))
                    .until((Function<WebDriver, Boolean>)webDriver -> title.equals(driver.getTitle()));
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

