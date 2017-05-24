package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class CloudTagFragment {

    @Root
    private WebElement cloudTagRoot;

    public PageNavigator navigate() {
        return new PageNavigator(cloudTagRoot);
    }
}
