package org.arquillian.tests.pom;

import org.arquillian.tests.utilities.FragmentNavigator;
import org.arquillian.tests.utilities.FragmentVerifier;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class SideBarFragment {

    @Root
    private WebElement sidebarRoot;

    public SideBarVerifier verify() {
        return new SideBarVerifier(sidebarRoot);
    }

    public SideBarNavigator navigate() {
        return new SideBarNavigator(sidebarRoot);
    }

    public class SideBarVerifier extends FragmentVerifier {
        public SideBarVerifier(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }

    public class SideBarNavigator extends FragmentNavigator {
        public SideBarNavigator(WebElement fragmentRoot) {
            super(fragmentRoot);
        }
    }
}
