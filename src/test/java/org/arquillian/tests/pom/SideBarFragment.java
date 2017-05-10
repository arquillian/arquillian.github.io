package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.FragmentNavigator;
import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class SideBarFragment {

    @Root
    private WebElement sidebarRoot;

    private String selector;

    public SideBarFragment(WebElement sidebarRoot, String selector) {
        this.sidebarRoot = sidebarRoot;
        this.selector = selector;
    }

    public SideBarVerifier verify() {
        return new SideBarVerifier(sidebarRoot, selector);
    }

    public SideBarNavigator navigate() {
        return new SideBarNavigator(sidebarRoot);
    }

    public class SideBarVerifier extends FragmentVerifier {
        public SideBarVerifier(WebElement sidebarRoot, String selector) {
            super(sidebarRoot, selector);
        }
    }

    public class SideBarNavigator extends FragmentNavigator {
        public SideBarNavigator(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }
}
