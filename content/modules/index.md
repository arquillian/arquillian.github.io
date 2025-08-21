---
title: Modules
description: Explore the various modules that make up the Arquillian Universe
layout: page
---

# Arquillian Modules

The Arquillian Universe is a collection of projects that work together to provide a comprehensive testing platform for the JVM. Here are the main modules:

## Core Modules

### Arquillian Core

The foundation of the Arquillian Universe, providing the core functionality for running tests in containers.

[Learn More](/modules/arquillian-core)

### ShrinkWrap

A Java API for creating archives (JAR, WAR, EAR) programmatically. ShrinkWrap makes it easy to create test deployments without having to manually create archive files.

[Learn More](/modules/shrinkwrap)

### Resolver

The ShrinkWrap Resolvers project provides a Java API to obtain artifacts from a repository system. This is handy to include third party libraries available in any Maven repository in your test archive.

[Learn More](/modules/resolver)

### Descriptors

The Shrinkwrap Descriptor project provides a uniformed fluent API for creating and modifying Java EE deployment descriptors on the fly.

[Learn More](/modules/descriptors)

## Extension Modules

### Drone

Browser automation and WebDriver integration for Arquillian. Drone makes it easy to test web applications with Selenium WebDriver.

[Learn More](/modules/drone)

### Graphene

Rich WebDriver extensions and Ajax support. Graphene extends Drone with additional functionality for testing rich web applications.

[Learn More](/modules/graphene)

### Persistence

Database testing support for Arquillian. The Persistence extension makes it easy to test JPA applications.

[Learn More](/modules/persistence)

### Transaction

Transaction management support for Arquillian. The Transaction extension makes it easy to test transactional applications.

[Learn More](/modules/transaction)

### Cube

Docker container integration for Arquillian. Cube allows you to manage Docker containers as part of your test lifecycle.

[Learn More](/modules/cube)

### Chameleon

Simplified container configuration for Arquillian. Chameleon makes it easy to switch between different containers without changing your test code.

[Learn More](/modules/chameleon)

### Algeron

Consumer-driven contract testing for Arquillian. Algeron makes it easy to test microservices that communicate with each other.

[Learn More](/modules/algeron)

### Smart Testing

Intelligent test execution order for Arquillian. Smart Testing makes your tests run faster by running the most relevant tests first.

[Learn More](/modules/smart-testing)

## Container Adapters

Arquillian supports a wide range of containers through container adapters. These adapters allow Arquillian to deploy your tests to the container and execute them inside the container.

- JBoss AS / WildFly
- GlassFish
- WebLogic
- WebSphere
- Tomcat
- Jetty
- Weld SE
- OpenEJB
- And many more...

[View All Container Adapters](/modules/container-adapters)