require 'ostruct'
require 'awestruct/util/inflector'

require_relative '../_ext/arquillian.rb'

describe Awestruct::Extensions::Repository::Visitors::GenericGradleComponent do

  class Cloner
    include Awestruct::Extensions::Repository::Visitors::Clone
  end

  class GradleVisitor
    include Awestruct::Extensions::Repository::Visitors::GenericGradleComponent
  end

  before :each do
    @visitor = GradleVisitor.new
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
    @repository.clone_url = 'git://github.com/arquillian/arquillian-gradle-plugin.git'
    link_components_modules
    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    
    @site.components.size.should equal(1)

    @site.components.each_value do |comp|
      puts comp
      comp.name.should eql('Gradle Arquillian plugin')
      comp.groupId.should eql('org.jboss.arquillian.gradle')
      comp.latest_version.should eql('0.1')

      comp.releases.should_not be_empty
    end
  end

end