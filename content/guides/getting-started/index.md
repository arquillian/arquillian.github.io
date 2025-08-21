---
title: Getting Started
description: Learn how to add Arquillian to the test suite of your project and write your first Arquillian test.
authors: mojavelinux
tags: cdi, weld, maven, forge, eclipse
guide_group: 1
guide_order: 10
layout: guide
---

This guide introduces you to Arquillian. After reading this guide, you'll be able to:

* Add the Arquillian infrastructure to a Maven-based Java project
* Write an Arquillian test that asserts the behavior of a CDI(Contexts and Dependency Injection) bean
* Execute the Arquillian test in multiple compatible containers in both Maven and Eclipse

You'll learn all of these skills by incorporating Arquillian into the test suite of a Java EE application built with Maven. We've designed this guide to be a _fast read_ to get you started quickly!

## Assumptions

The simplest way to get started with Arquillian is to incorporate it into the test suite of a project build that offers dependency management. Today, the most widely used build tool in this category is Apache Maven. This guide will navigate you to your first **green bar** using a new Maven project.

> Arquillian does not depend on Maven, or any specific build tool for that matter. It works just as well--if not better--when used in a project with an Ant or Gradle build. Ideally, the build tool should offer dependency management as it simplifies the task of including the Arquillian libraries since they are distributed in the Maven Central repository.

This guide assumes you have Maven available, either in your command shell or your IDE(Integrated Development Environment). If you don't, please download and install Maven now. You'll also need JDK(Java Development Kit) 1.5 or higher installed on your machine, though JDK 1.6 is preferred.

## Create a New Project

There are two ways we recommend you create a new Maven project:

1. Generate a project from a Maven archetype
2. Create and customize a project using JBoss Forge

By far, JBoss Forge is the simpler approach, but this guide will offer both options in the event you aren't ready to adopt JBoss Forge. Select from one of the two options above to jump to the instructions.

> If you already have a Maven project, you can use this section as review to ensure you have the proper dependencies before moving on.

## Add the Arquillian APIs

Once you have your project set up, you need to add the Arquillian APIs to your project. Open up the `pom.xml` file at the root of the project in your editor and add the following XML fragment directly above the `<build>` element to import the BOM(Bill of Materials), or version matrix, for Arquillian's transitive dependencies.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.jboss.arquillian</groupId>
            <artifactId>arquillian-bom</artifactId>
            <version>1.7.0.Final</version>
            <scope>import</scope>
            <type>pom</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Next, append the following XML fragment directly under the last `<dependency>` element to add the Arquillian JUnit integration:

```xml
<dependency>
    <groupId>org.jboss.arquillian.junit</groupId>
    <artifactId>arquillian-junit-container</artifactId>
    <scope>test</scope>
</dependency>
```

## Write an Arquillian Test

An Arquillian test case must have three things:

1. A `@RunWith(Arquillian.class)` annotation on the class
2. A public static method annotated with `@Deployment` that returns a ShrinkWrap archive
3. At least one method annotated with `@Test`

Here's a simple example:

```java
package org.arquillian.example;

import javax.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class GreeterTest {

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClass(Greeter.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    Greeter greeter;

    @Test
    public void should_create_greeting() {
        Assert.assertEquals("Hello, Earthling!",
            greeter.createGreeting("Earthling"));
        greeter.greet(System.out, "Earthling");
    }
}
```

## Add a Container Adapter

Arquillian selects the target container based on which container adapter is available on the test classpath. A _container adapter_ controls and communicates with a container (e.g., Weld Embedded, JBoss AS, GlassFish, etc). That means we'll need to add additional libraries to the project.

Here's an example of adding the Weld EE embedded container adapter:

```xml
<dependency>
    <groupId>org.jboss.arquillian.container</groupId>
    <artifactId>arquillian-weld-ee-embedded-1.1</artifactId>
    <version>1.0.0.CR9</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.jboss.weld</groupId>
    <artifactId>weld-core</artifactId>
    <version>2.3.5.Final</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.6.4</version>
    <scope>test</scope>
</dependency>
```

## Run the Arquillian Test

Once you add all the necessary Arquillian libraries to the classpath, you can run an Arquillian test just like a unit test, whether you are running it from the IDE, the build script or any other test plugin.

From the IDE window, right click on the GreeterTest.java file in the Package Explorer (or in the editor) and select Run As > JUnit Test from the context menu.

When you run the test, you should see the following lines printed to the console:

```
INFO org.jboss.weld.Version - WELD-000900 2.3.5 (Final)
Hello, Earthling!
```

You should then see the JUnit view appear, revealing a **green bar**!

**Congratulations!** You've earned your first **green bar** with Arquillian!