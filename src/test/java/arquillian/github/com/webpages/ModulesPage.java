package arquillian.github.com.webpages;

import org.openqa.selenium.By;

//@RunWith(Arquillian.class)
public class ModulesPage {

    public static By modulesPage = By.xpath("//*[@id='content-header']/div/h1");
    /*  @Drone
    WebDriver driver;

    private static String ARQUILLIAN_URL = "http://arquillian.org/";

    @FindBy(linkText = "Modules")
    private WebElement modulesTab;

    @FindBy(xpath = "/*//*[@class='well']//descendant::a")
    private List<WebElement> moduleList;

    public boolean isModuleReachable() {
        for (WebElement module : moduleList) {
            String expectedModuleTitle = formatTitle(module.getText());
            String expectedModuleURL = module.getAttribute("href");

            module.click();

            String actualModuleTitle = driver.getTitle();
            String actualModuleURL = driver.getCurrentUrl();

            if (actualModuleTitle.equals(expectedModuleTitle) && actualModuleURL.equals(expectedModuleURL)) {
                return true;
            }
            return false;
        }
        return true;
    }

    private String formatTitle(String title) {
        String version = "SNAPSHOT";
        if (title.contains(version)) {
            String[] titleArray = title.split(version);
            return titleArray[0];
        }
        return title.substring(0, title.indexOf("–"));
    }*/
}
