require 'ostruct'
require 'awestruct/util/inflector'

require_relative '../_ext/arquillian.rb'

describe Awestruct::Extensions::Repository::Visitors::ContainerComponent do

  class Cloner
    include Awestruct::Extensions::Repository::Visitors::Clone
  end

  class ContainerVisitor
    include Awestruct::Extensions::Repository::Visitors::ContainerComponent
  end

  before :each do
    @visitor = ContainerVisitor.new
    @site = OpenStruct.new
    @site.tmp_dir = '/tmp/'
    @site.repos_dir = '/tmp/arqrepos'
    @site.modules = {}
    @repository = OpenStruct.new(
            :path => 'arquillian-container-test',
            :desc => nil,
            :relative_path => '',
            :owner => 'arquillian',
            :host => 'github.com',
            :type => 'git',
            :master_branch => 'master'
            #:html_url => 'https://github.com/forge/plugin-arquillian',
            #:clone_url => 'git://github.com/forge/plugin-arquillian.git'
          )
  end

  def link_components_modules
    path = $1 if @repository.clone_url =~/.*\/(.*)\.git/
    @site.components = {
      path => OpenStruct.new(
        :modules => [],
        :releases => []
      )
    }
    @repository.path = path
  end

  it "should discover containers in named subfolders" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-weld.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(3)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian Weld EE Embedded 1.1.x Container Adapter')
      expect(names).to include('Arquillian Weld SE Embedded 1.1.x Container Adapter')
      expect(names).to include('Arquillian Weld SE Embedded 1.x Container Adapter')
    end    
  end

  it "should discover containers in non named subfolders" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-osgi.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(6)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian OSGi :: Container :: JBoss :: Embedded')
    end
  end

  it "should discover containers in non arquillian org repository" do
    @repository.clone_url = 'git://github.com/wildfly/wildfly-arquillian.git'
    #@repository.relative_path = 'arquillian/'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)
    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(4)

      names = comp.modules.map {|x| x.name}
      # Temp removed from build
      #expect(names).to include('Arquillian WildFly Embedded Container Adapter')
      expect(names).to include('Arquillian WildFly Remote Container Adapter')
      expect(names).to include('Arquillian WildFly Managed Container Adapter')
      expect(names).to include('Arquillian WildFly Remote Domain Container Adapter')
      expect(names).to include('Arquillian WildFly Managed Domain Container Adapter')
    end
  end

  it "should only discover modules that are included in build" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-glassfish.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(3)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian GlassFish Embedded 3.1 Container Adapter')
      expect(names).to include('Arquillian GlassFish Remote 3.1 Container Adapter')
      expect(names).to include('Arquillian GlassFish Managed 3.1 Container Adapter')
    end
  end

  it "should discover OpenShift specially, not using embedded|managed|remote naming" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-openshift.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(1)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian OpenShift Container Adapter')
    end
  end

  it "should discover CloudBees specially, not using embedded|managed|remote naming" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-cloudbees.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(1)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian Cloudbees Container Adapter')
    end
  end

  it "should discover OpenShift specially, relocated artifact" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-openshift.git'
    link_components_modules
    @site.components['arquillian-container-openshift'].latest_tag = '1.0.0.Beta1'
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(1)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian OpenShift Express Container Adapter')
    end
  end

  it "should discover TomEE and OpenEJB External Containers" do
    @repository.clone_url = 'git://github.com/apache/tomee.git'
    @repository.relative_path = 'arquillian/'
    @repository.master_branch = 'trunk'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(3)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian TomEE Embedded Container Adapter')
      expect(names).to include('Arquillian TomEE Remote Container Adapter')
      expect(names).to include('Arquillian OpenEJB Container Adapter')
    end
  end

  it "should discover WAS containers in profiles" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-was.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)

    @site.components.each_value do |comp|
      expect(comp.modules.size).to eql(5)

      names = comp.modules.map {|x| x.name}
      expect(names).to include('Arquillian WebSphere AS Remote 7.x Container Adapter')
      expect(names).to include('Arquillian WebSphere AS Embedded 8.x Container Adapter')
      expect(names).to include('Arquillian WebSphere AS Remote 8.0 Container Adapter')
      expect(names).to include('Arquillian WebSphere AS Remote 8.5 Container Adapter')
      expect(names).to include('Arquillian WebSphere Liberty Profile Managed 8.5 Container Adapter')
    end
  end
end