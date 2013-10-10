require 'ostruct'
require 'awestruct/util/inflector'

require_relative '../_ext/arquillian.rb'

describe Awestruct::Extensions::Repository::Visitors::ExtensionComponent do

  class Cloner
    include Awestruct::Extensions::Repository::Visitors::Clone
  end

  class ExtensionVisitor
    include Awestruct::Extensions::Repository::Visitors::ExtensionComponent
  end

  before :each do
    @visitor = ExtensionVisitor.new
    @site = OpenStruct.new
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
        :modules => [],
        :repository => @repository
      )
    }
    @repository.path = path
  end

  it "should discover depchain pom" do
    @repository.clone_url = 'git://github.com/arquillian/arquillian-graphene.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      comp.modules.size.should equal(1)

      comp.modules.each do |mod|
        mod.artifacts.size.should equal(1)

        #puts mod.artifacts[0].coordinates.to_maven_dep
        artifact = mod.artifacts[0].coordinates
        expect(artifact.groupId).to eq("org.jboss.arquillian.graphene")
        expect(artifact.artifactId).to eq("arquillian-graphene")
        expect(artifact.packaging).to eq(:pom)

      end

    end
  end

end