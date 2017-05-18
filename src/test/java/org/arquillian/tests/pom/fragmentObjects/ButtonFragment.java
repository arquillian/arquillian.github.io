package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class ButtonFragment {

    @Root
    private WebElement buttonRoot;

    public PageNavigator navigate() {
        return new PageNavigator(buttonRoot);
    }
}
