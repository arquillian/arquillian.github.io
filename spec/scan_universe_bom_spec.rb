require 'ostruct'
require 'awestruct/util/inflector'

require_relative '../_ext/arquillian.rb'

describe Awestruct::Extensions::Repository::Visitors::ArquillianUniverseComponent do

  class Cloner
    include Awestruct::Extensions::Repository::Visitors::Clone
  end

  class UniverseVisitor
    include Awestruct::Extensions::Repository::Visitors::ArquillianUniverseComponent
  end

  before :each do
    @visitor = UniverseVisitor.new
    @site = OpenStruct.new
    @site.dir = File.expand_path (File.dirname(__FILE__) + "/../")
    @site.tmp_dir = '/tmp/'
    @site.repos_dir = '/tmp/arqrepos'
    @site.modules = {}
    @site.component_leads = {}
    @site.git_author_index = {}
    @repository = OpenStruct.new(
            :path => 'arquillian-universe-bom',
            :desc => nil,
            :relative_path => '',
            :owner => 'arquillian',
            :host => 'github.com',
            :type => 'git',
            :master_branch => 'master',
            :html_url => 'https://github.com/arquillian/arquillian-universe-bom',
            :clone_url => 'git://github.com/arquillian/arquillian-universe-bom.git'
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

  it "should discover sub modules" do
    link_components_modules

    Cloner.new().visit(@repository, @site)

    @visitor.visit(@repository, @site)
    expect(@site.components.size).to equal(1)
    expect(@site.components['arquillian-universe-bom'].modules.size).to be > 1

  end

end
