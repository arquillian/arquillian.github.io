package org.arquillian.tests.pagetests;

import org.arquillian.tests.pom.pageObjects.DocsPage;
import org.arquillian.tests.pom.pageObjects.MainPage;
import org.arquillian.tests.pom.pageObjects.StandalonePage;
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
public class DocsPageTest {

    @Drone
    private WebDriver driver;

    @Page
    private MainPage mainPage;

    @Page
    private DocsPage docsPage;

    @Page
    private StandalonePage fetchedDocumentationPage;

    @Before
    public void open() {
        driver.navigate().to("http://arquillian.org");
    }

    @Test
    public void should_have_a_listing_of_all_docs() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .verify()
            .containsEntries("Core", "Algeron Extension", "Cube Extension", "Cube Q Extension", "Drone Extension",
                "Extension Performance", "Persistence Extension", "Warp", "Graphene");
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_core() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Core");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian - So you can rule your code. Not the bugs.")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_algeron_extension() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Algeron Extension");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Algeron")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_cube() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Cube Extension");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Cube")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_cubeQ() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Cube Q Extension");

        fetchedDocumentationPage.verify()
            .hasTitle("Introduction")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_drone() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Drone Extension");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Drone")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_performance() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Extension Performance");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Performance Extension")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_persistence() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Persistence Extension");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Persistence Extension")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_warp() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Warp");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Warp")
            .hasContent();
    }

    @Test
    public void should_be_able_to_go_to_documentation_for_graphene() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.navigationList()
            .navigate().to("Graphene");

        fetchedDocumentationPage.verify()
            .hasTitle("Arquillian Graphene 2")
            .hasContent();
    }

   @Test
    public void should_have_all_core_concepts_menu_items() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .verify()
            .hasMenuItemsDisplayedInOrder("Test runners", "Deployments", "Test enrichers", "Containers", "Run modes", "Extensions");
    }
    @Test
    public void should_display_content_for_test_runners_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Test runners");

        docsPage.content()
            .verify()
            .containsDescForItem("Test runners");
    }

    @Test
    public void should_display_content_for_deployments_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Deployments");

        docsPage.content()
            .verify()
            .containsDescForItem("Deployments");
    }

    @Test
    public void should_display_content_for_test_enrichers_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Test enrichers");

        docsPage.content()
            .verify()
            .containsDescForItem("Test enrichers");
    }

    @Test
    public void should_display_content_for_containers_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Containers");

        docsPage.content()
            .verify()
            .containsDescForItem("Containers");
    }

    @Test
    public void should_display_content_for_run_modes_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Run modes");

        docsPage.content()
            .verify()
            .containsDescForItem("Run modes");
    }

    @Test
    public void should_display_content_for_extensions_menu_item() throws Exception {
        mainPage.menu()
            .navigate().to("Docs");

        docsPage.menu()
            .navigate().to("Extensions");

        docsPage.content()
            .verify()
            .containsDescForItem("Extensions");
    }
}