package arquillian.github.com.webpages;

import org.openqa.selenium.By;

public class ArquillianHomePage {

    public static By invasionTab = By.linkText("Invasion!");
    public static By featuresTab = By.linkText("Features");
    public static By guidesTab = By.linkText("Guides");
    public static By docsTab = By.linkText("Docs");
    public static By blogTab = By.linkText("Blog");
    public static By communityTab = By.linkText("Community");
    public static By modulesTab = By.linkText("Modules");

    public static By getStartedButton = By.linkText("Get Started!");

    public static By blogPostList = By.xpath("//*[@id='news']/ul/li/h3/a");
}
