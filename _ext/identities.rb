require 'json'
require_relative 'restclient_extensions'
require_relative 'identities/github'
require_relative 'identities/gravatar'
require_relative 'identities/confluence'
require_relative 'identities/jbosscommunity'

module Awestruct::Extensions::Identities
  # QUESTION should we call this Initializer or Loader instead?
  class Storage
    def initialize(opts = {})
      @use_data_cache = opts[:use_data_cache] || true
    end

    def execute(site)
      data_file = File.join(site.tmp_dir, 'datacache', 'identities.yml')
      loaded = false
      identities = []
      if @use_data_cache and File.exist? data_file
        identities = YAML.load_file data_file
        loaded = true
      else
        if site.identities
          identities = site.identities.map{|i| OpenStruct.new i}
        end
      end
      identities.extend(Locators)
      identities.loaded = loaded
      site.identities = identities
    end

    module Locators
      attr_accessor :loaded

      def lookup(username, create = false)
        return nil if username.nil?
        identity = self.find {|e| username.eql? e.username} ||
            self.find {|e| username.eql? e.jboss_username}
        if create and identity.nil?
          identity = OpenStruct.new({:username => username}) 
          if block_given?
            yield identity
          end
          self << identity
        end
        identity
      end
  
      def lookup_by_name(name, create = false)
        return nil if name.nil?
        search = name.downcase
        identity = self.find {|e| search.eql? e.name.to_s.downcase}
        if create and identity.nil?
          identity = OpenStruct.new({:name => name}) 
          if block_given?
            yield identity
          end
          self << identity
        end
        identity
      end

      def lookup_by_contributor(contributor)
        identity = self.find {|e| e.contributor and e.contributor.emails and e.contributor.emails.include? contributor.email }
        if identity.nil?
          # Indication that we have a mismatched account
          puts "Could not find contributor with e-mail " + contributor.email
        end
        identity
      end
  
      def lookup_by_email(email, create = false)
        return nil if email.nil?
        identity = self.find {|e| email.eql? e.email or !e.emails.nil? and e.emails.include? email}
        if create and identity.nil?
          identity = OpenStruct.new({:email => email}) 
          if block_given?
            yield identity
          end
          self << identity
        end
        identity
      end

      def lookup_by_github_id(github_id, create = false)
        return nil if github_id.nil?
        identity = self.find {|e| github_id.eql? e.github_id}
        if create and identity.nil?
          identity = OpenStruct.new({:github_id => github_id}) 
          if block_given?
            yield identity
          end
          self << identity
        end
        identity
      end
  
      def lookup_by_twitter_username(username)
        return nil if username.nil?
        self.find {|e| !e.twitter.nil? and username.eql? e.twitter.username }
      end
  
      def locate(*query)
        query.map! {|e| e.downcase}
        self.find {|e| not ([e.username, e.name.to_s.downcase, e.email] & query).empty? }
      end

      def core_team()
        self.select {|e| e.core}
      end
  
      def contributors()
        self.select {|e| e.contributor}
      end
  
      def speakers()
        self.select {|e| e.speaker}
      end
  
      def translators()
        self.select {|e| e.translator}
      end
    end
  end

  module IdentityHelper
    # SEMI-HACK this should received contributions from crawlers
    def url
      self.jbosscommunity ? self.jbosscommunity.profile_url : self.github.profile_url 
    end

    # TODO support for CSS class, or just attributes in general
    def to_link
      "<a href=\"#{self.url}\">#{self.name}</a>"
    end

    def to_s
      self.name
    end
  end

  class Collect
    def initialize(*collectors)
      @collectors = collectors
    end

    def execute(site)
      if !site.identities.loaded
        @collectors.each do |c|
          c.collect(site.identities)
        end
      end
    end
  end

  class Crawl
    def initialize(*crawlers)
      @crawlers = crawlers
    end

    def execute(site)
      site.identities.each do |i|
        @crawlers.each do |c|
          c.crawl(i) if !site.identities.loaded
          i.extend(IdentityHelper)
          c.enhance(i) if c.respond_to? 'enhance'
        end
      end
    end
  end

  class Cache
    def execute(site)
      data_file = File.join(site.tmp_dir, 'datacache', 'identities.yml')
      FileUtils.mkdir_p File.dirname data_file
      File.open(data_file, 'w') do |out|
        YAML.dump site.identities, out
      end
    end
  end
end
