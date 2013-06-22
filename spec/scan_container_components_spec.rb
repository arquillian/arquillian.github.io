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
        :modules => []
      )
    }
    @repository.path = path
  end

  it "should discover containers in named subfolders" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-weld.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(3)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian Weld EE Embedded 1.1.x Container Adapter')
      names.should include('Arquillian Weld SE Embedded 1.1.x Container Adapter')
      names.should include('Arquillian Weld SE Embedded 1.x Container Adapter')
    end    
  end

  it "should discover containers in non named subfolders" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-osgi.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(1)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian OSGi Container Embedded')
    end
  end

  it "should discover containers in non arquillian org repository" do
    @repository.clone_url = 'git://github.com/wildfly/wildfly.git'
    @repository.relative_path = 'arquillian/'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)
    @site.components.each_value do |comp|
      comp.modules.size.should eql(5)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian WildFly 8 Embedded Container Adapter')
      names.should include('Arquillian WildFly 8 Remote Container Adapter')
      names.should include('Arquillian WildFly 8 Managed Container Adapter')
      names.should include('Arquillian WildFly 8 Remote Domain Container Adapter')
      names.should include('Arquillian WildFly 8 Managed Domain Container Adapter')
    end
  end

  it "should only discover modules that are included in build" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-glassfish.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(3)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian GlassFish Embedded 3.1 Container Adapter')
      names.should include('Arquillian GlassFish Remote 3.1 Container Adapter')
      names.should include('Arquillian GlassFish Managed 3.1 Container Adapter')
    end
  end

  it "should discover OpenShift specially, not using embedded|managed|remote naming" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-openshift.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(1)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian OpenShift Container Adapter')
    end
  end

  it "should discover OpenShift specially, relocated artifact" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-openshift.git'
    link_components_modules
    @site.components['arquillian-container-openshift'].latest_tag = '1.0.0.Beta1'
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(1)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian OpenShift Express Container Adapter')
    end
  end

  it "should discover TomEE and OpenEJB External Containers" do
    @repository.clone_url = 'git://github.com/apache/tomee.git'
    @repository.relative_path = 'arquillian/'
    @repository.master_branch = 'trunk'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should eql(3)

      names = comp.modules.map {|x| x.name}
      names.should include('Arquillian TomEE Embedded Container Adapter')
      names.should include('Arquillian TomEE Remote Container Adapter')
      names.should include('Arquillian OpenEJB Container Adapter')
    end
  end
end