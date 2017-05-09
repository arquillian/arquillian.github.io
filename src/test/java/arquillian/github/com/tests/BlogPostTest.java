package arquillian.github.com.tests;

import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;

@RunWith(Arquillian.class)
public class BlogPostTest {

    @Drone
    WebDriver driver;

    @Test
    public void verify_blog_posts_are_listed_on_landing_page() {
    }
}
