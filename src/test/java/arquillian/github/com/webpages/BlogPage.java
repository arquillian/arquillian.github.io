package arquillian.github.com.webpages;

import org.openqa.selenium.By;

public class BlogPage {


    public static By blogPage = By.xpath("//*[@id='content-header']/div/h1/a");


    /*
    @FindBy(linkText = "Arquillian Blog")
    private WebElement heading;


    @FindBy(className = "post")
    private List<WebElement> blogPosts;

    public BlogPage(WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(driver, this);
    }

    public boolean hasBlogPosts() {
        waitGui().until().element(By.linkText("Arquillian Blog")).is().present();

        if (blogPosts.size() > 0) {
            for (WebElement blogPost : blogPosts) {
                if (blogPost.getText().contains("Released")) {
                    return true;
                }
            }
        }
        return false;
    }
    */
}
