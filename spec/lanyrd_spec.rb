require_relative '../_ext/lanyrd.rb'
require 'ostruct'
require 'awestruct/util/inflector'


describe Awestruct::Extensions::Lanyrd::Search do

  before :each do
    @site = OpenStruct.new
    @site.tmp_dir = '/tmp/'
    @site.repos_dir = '/tmp/arqrepos'
    @site.modules = {}
    @site.component_leads = {}
    @site.git_author_index = {}
  end

  # Manual test for now
  xit "should be able to parse search result" do
    ext = Awestruct::Extensions::Lanyrd::Search.new("arquillian")
    ext.execute(@site)

    puts @site.sessions
  end
end