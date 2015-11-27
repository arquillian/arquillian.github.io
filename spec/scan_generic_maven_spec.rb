require 'ostruct'
require 'awestruct/util/inflector'

require_relative '../_ext/arquillian.rb'

describe Awestruct::Extensions::Repository::Visitors::GenericMavenComponent do

  class Cloner 
    include Awestruct::Extensions::Repository::Visitors::Clone
  end

  class MavenVisitor 
    include Awestruct::Extensions::Repository::Visitors::GenericMavenComponent
  end

  before :each do
    @visitor = MavenVisitor.new
    @site = OpenStruct.new
    @site.dir = File.expand_path (File.dirname(__FILE__) + "/../")
    @site.tmp_dir = '/tmp/'
    @site.repos_dir = '/tmp/arqrepos'
    @site.modules = {}
    @site.component_leads = {}
    @site.git_author_index = {}
    @repository = OpenStruct.new(
            :path => 'arquillian-container-test',
            :desc => nil,
            :relative_path => '',
            :owner => 'arquillian',
            :host => 'github.com',
            :type => 'git',
            :master_branch => 'master',
            :html_url => 'https://github.com/forge/plugin-arquillian',
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

  it "should discover versions with prefix" do
    @repository.clone_url = 'git://github.com/apache/tomee.git'
    @repository.relative_path = 'arquillian/'
    @repository.master_branch = 'trunk'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      expect(comp.releases.size).to be > 3

      versions = comp.releases.map {|x| x.version}
      versions.should include('1.5.0')
      versions.should include('1.5.1')
      versions.should include('1.5.2')

      tags = comp.releases.map {|x| x.tag}
      tags.should include('tomee-1.5.0')
      tags.should include('tomee-1.5.1')
      tags.should include('tomee-1.5.2')
    end
  end

  # version.org.jboss.arquillian | version.org.jboss.shrinkwrap
  it "should discover compile deps with non standard naming" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-osgi.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      
      deps = comp.releases.last.compiledeps.map {|x| x.name}
      deps.should include('Arquillian Core')
      deps.should include('ShrinkWrap Core')
    end
  end

  # found in /relative_path/pom.xml not /pom.xml
  it "should discover compile deps from non root pom" do
    @repository.clone_url = 'git://github.com/apache/tomee.git'
    @repository.relative_path = 'arquillian/'
    @repository.master_branch = 'trunk'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      
      deps = comp.releases.last.compiledeps.map {|x| x.name}
      deps.should include('Arquillian Core')
      deps.should include('ShrinkWrap Core')
    end
  end

  it "should discover QUnit name" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-extension-qunit.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.name.should eql('Arquillian Extension QUnit')
    end
  end

  it "should discover WebSphere name" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-container-was.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.name.should eql('Arquillian Container WebSphere AS')
    end
  end

  it "should discover TestRunner Spock name" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-testrunner-spock.git'

    @site.component_leads = {'arquillian-testrunner-spock' => ''}
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.name.should eql('Arquillian TestRunner Spock')
    end
  end

  it "should extract published artifacts" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-extension-drone.git'

    link_components_modules
    @site.resolve_published_artifacts = true
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components.size.should equal(1)
    puts @site.components['arquillian-extension-drone'].releases.last.published_artifacts.each {|a|
      puts a.to_s
    }
  end

  it "should extract published artifacts for Universe POMs" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-universe-bom.git'

    link_components_modules
    @site.resolve_published_artifacts = true
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components['arquillian-universe-bom'].releases.last.published_artifacts.size.should equal(44)
  end

  it "should resolve recursive known versions" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-droidium.git'

    link_components_modules
    @site.resolve_published_artifacts = true
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    @site.components['arquillian-droidium'].releases.last.compiledeps.each do |dep|
      dep.version.should_not include('${')
    end
  end
end