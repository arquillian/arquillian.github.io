package org.arquillian.tests.pom.fragmentObjects;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.arquillian.tests.utilities.GitHubProjectVersionExtractor;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.Graphene;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

public class NavigationListFragment {

    @Drone
    private WebDriver driver;

    @Root
    private WebElement navListRoot;

    public NavigationListVerifier verify() {
        return new NavigationListVerifier();
    }

    public class NavigationListVerifier {

        public NavigationListVerifier containsEntries(String... expectedItems) {
            final List<WebElement> fragmentItems = navListRoot.findElements(By.cssSelector("li a"));
            List<String> fragmentItemsTitles =
                fragmentItems.stream().map(list -> formatTitle(list.getText())[0].trim()).collect(Collectors.toList());

            assertThat(fragmentItemsTitles).contains(expectedItems);

            return this;
        }

        public NavigationListVerifier containsEntriesWithLatestVersion(String... expectedItems) {
            Arrays.stream(expectedItems).forEach(expectedItem -> {
                    List<WebElement> fragmentItems = navListRoot.findElements(By.cssSelector("li a"));
                    fragmentItems.stream()
                        .filter(list -> formatTitle(list.getText())[0].trim().contains(expectedItem))
                        .forEach(list -> {
                            String latestVersion = formatTitle(list.getText())[1].trim();
                            String project = getProjectRepo(list);
                            assertThat(latestVersion)
                                .isEqualTo(new GitHubProjectVersionExtractor(project).getLatestReleaseFromGitHub());
                        });
                }
            );
            return this;
        }

        private String getProjectRepo(WebElement list) {
            final By xpath = By.xpath(".//dt[contains(text(),'Web URL')]/following-sibling::dd[1]");
            list.click();
            Graphene.waitModel().until().element(xpath).is().visible();
            String project = driver.findElement(xpath).getText();
            driver.navigate().to("http://arquillian.org/modules/");
            return project;
        }

        private String[] formatTitle(String title) {
            if (title.contains("SNAPSHOT")) {
                return title.split("((?<=SNAPSHOT)|(?=SNAPSHOT))");
            }
            return title.split("–");
        }
    }

    public PageNavigator navigate() {
        return new PageNavigator(navListRoot);
    }
}
