package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.FragmentNavigator;
import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class MenuFragment {

    @Root
    private WebElement menuRoot;

    private String selector;

    public MenuFragment(WebElement menuRoot, String selector) {
        this.menuRoot = menuRoot;
        this.selector = selector;
    }

    public MenuVerifier verify() {
        return new MenuVerifier(menuRoot, selector);
    }

    public MenuNavigator navigate() {
        return new MenuNavigator(menuRoot);
    }

    public class MenuVerifier extends FragmentVerifier {
        public MenuVerifier(WebElement menuRoot, String selector) {
            super(menuRoot, selector);
        }
    }

    public class MenuNavigator extends FragmentNavigator {
        public MenuNavigator(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }
}
