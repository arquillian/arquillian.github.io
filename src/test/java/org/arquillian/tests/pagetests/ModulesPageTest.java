package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.MainPage;
import org.arquillian.tests.pom.pageObjects.ModulesPage;
import org.arquillian.tests.pom.pageObjects.StandaloneModulePage;
import org.arquillian.tests.pom.pageObjects.StandalonePage;
import org.arquillian.tests.utilities.ArquillianBlogInstance;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

@RunWith(Arquillian.class)
@RunAsClient
public class ModulesPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private ModulesPage modulesPage;

    @Page
    private StandaloneModulePage fetchedModulePage;

    @Page
    private StandalonePage fetchedDocumentationPage;

    @Before
    public void open() {
        driver.navigate().to(ArquillianBlogInstance.getUrl());
    }

    @Test
    public void should_have_a_listing_of_modules() throws Exception {
        mainPage.menu()
            .navigate().to("Modules");

        modulesPage.navigationList()
            .verify()
            .containsEntries("Universe", "Core", "Daemon", "GlassFish Embedded 3.1 Container Adapter",
                "arquillian-gradle-plugin", "ShrinkWrap Resolver" );
    }

    @Test
    public void should_be_able_to_go_to_module_page_without_documentation() throws Exception {
        mainPage.menu()
            .navigate().to("Modules");

        modulesPage.navigationList()
            .navigate().to("Universe");

        fetchedModulePage.verify().hasTitle("Arquillian Universe · Arquillian")
            .hasModuleSummary()
            .hasSourceRepoInfo()
            .hasSections("activity", "artifacts", "releases", "contributors")
            .hasDocumentation(false);
    }

    @Test
    public void should_be_able_to_go_to_module_page_with_documentation() throws Exception {
        mainPage.menu()
            .navigate().to("Modules");

        modulesPage.navigationList()
            .navigate().to("Core");

        fetchedModulePage.verify().hasTitle("Arquillian Core · Arquillian")
            .hasModuleSummary()
            .hasSourceRepoInfo()
            .hasSections("activity", "artifacts", "releases", "contributors")
            .hasDocumentation(true);
    }

    @Test
    public void should_navigate_to_documentation_page_if_present() throws Exception {
        mainPage.menu()
            .navigate().to("Modules");

        modulesPage.navigationList()
            .navigate().to("Core");

        fetchedModulePage.verify()
            .hasTitle("Arquillian Core · Arquillian")
            .hasModuleSummary()
            .hasSourceRepoInfo()
            .hasSections("activity", "artifacts", "releases", "contributors")
            .hasDocumentation(true);

        fetchedModulePage.navigate().to("Documentation");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian - So you can rule your code. Not the bugs.")
            .hasContent();
    }
}
