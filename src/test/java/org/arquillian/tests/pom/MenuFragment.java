package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.FragmentNavigator;
import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class MenuFragment {

    @Root
    private WebElement menuRoot;

    public MenuVerifier verify() {
        return new MenuVerifier(menuRoot);
    }

    public MenuNavigator navigate() {
        return new MenuNavigator(menuRoot);
    }

    public class MenuVerifier extends FragmentVerifier {
        public MenuVerifier(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }

    public class MenuNavigator extends FragmentNavigator {
        public MenuNavigator(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }
}
