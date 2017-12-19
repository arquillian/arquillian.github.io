package org.arquillian.tests.pom.fragmentObjects;

import org.arquillian.tests.utilities.BlogVerifier;
import org.arquillian.tests.utilities.PageNavigator;
import org.jboss.arquillian.graphene.fragment.Root;
import org.openqa.selenium.WebElement;

public class BlogFragment {

    @Root
    private WebElement contentRoot;

    public BlogVerifier verify() {
        return new BlogVerifier();
    }

    public PageNavigator navigate() {
        return new PageNavigator(contentRoot);
    }
}
