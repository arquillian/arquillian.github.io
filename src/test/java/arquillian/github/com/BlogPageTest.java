package arquillian.github.com;

import java.util.List;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.arquillian.graphene.Graphene.waitGui;

@RunWith(Arquillian.class)
public class BlogPageTest {

    @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    @FindBy(linkText = "Blog")
    private WebElement blogTab;

    @FindBy(className = "post")
    private List<WebElement> blogPosts;

    @Before
    public void ensure_successful_loading_of_blog_page() {
        driver.get(ARQUILLIAN_URL);
        blogTab.click();
        waitGui().until().element(By.linkText("Arquillian Blog")).is().present();
    }

    @Test
    public void verify_blog_posts_are_listed_on_landing_page() {
        assertThat(blogPosts).size().isPositive();

        for (WebElement blogPost : blogPosts) {
            assertThat(blogPost.getText()).contains("Released");
        }
    }
}
