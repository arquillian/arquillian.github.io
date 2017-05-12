package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.FragmentVerifier;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class SideBarFragment {

    @Root
    private WebElement sidebarRoot;

    private String selector;

    private String navigationSelector;

    public SideBarFragment(WebElement sidebarRoot, String selector, String navigationSelector) {
        this.sidebarRoot = sidebarRoot;
        this.selector = selector;
        this.navigationSelector = navigationSelector;
    }

    public SideBarVerifier verify() {
        return new SideBarVerifier(sidebarRoot, selector);
    }

    public PageNavigator navigate() {
        return new PageNavigator(sidebarRoot, navigationSelector);
    }

    public class SideBarVerifier extends FragmentVerifier {
        public SideBarVerifier(WebElement sidebarRoot, String selector) {
            super(sidebarRoot, selector);
        }
    }
}
