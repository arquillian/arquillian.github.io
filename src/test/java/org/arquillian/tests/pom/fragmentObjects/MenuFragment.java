package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.PageNavigator;
import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class MenuFragment {

    @Root
    private WebElement menuRoot;

    private String selector;

    private String navigationSelector;

    public MenuFragment(WebElement menuRoot, String selector, String navigationSelector) {
        this.menuRoot = menuRoot;
        this.selector = selector;
        this.navigationSelector = navigationSelector;
    }

    public MenuVerifier verify() {
        return new MenuVerifier(menuRoot, selector);
    }

    public PageNavigator navigate() {
        return new PageNavigator(menuRoot, navigationSelector);
    }

    public class MenuVerifier extends FragmentVerifier {
        public MenuVerifier(WebElement menuRoot, String selector) {
            super(menuRoot, selector);
        }
    }
}
