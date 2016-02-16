# encoding: utf-8
require 'git'
require 'fileutils'
require 'rexml/document'
require_relative 'repository'

class Artifact
  attr_reader :name, :coordinates

  def initialize(name, coordinates)
    @name = name
    @coordinates = coordinates
  end

  class Coordinates
    attr_reader :groupId, :artifactId, :packaging, :version
    def initialize(groupId, artifactId, packaging = :jar, version = nil)
      @groupId = groupId
      @artifactId = artifactId
      @packaging = packaging.nil? ? :jar : packaging.to_sym
      @version = version
    end

    def self.parse(coordinates_str)
      # The * spreads the array across the arguments of the constructor
      new(*coordinates_str.split(':'))
    end

    def to_maven_dep(opts = {})
      scope = opts[:scope] || :compile
      include_version = opts[:include_version].nil? ? true : opts[:include_version]
      buf = "<dependency>\n".
          concat("  <groupId>#{@groupId}</groupId>\n").
          concat("  <artifactId>#{@artifactId}</artifactId>\n")
      buf.concat("  <version>#{@version}</version>\n") if include_version and !version.nil?
      buf.concat("  <type>#{@packaging}</type>\n") if !packaging.to_sym.eql? :jar
      buf.concat("  <scope>#{scope}</scope>\n") if !scope.to_sym.eql? :compile
      buf.concat("</dependency>")
    end

    def to_url(base_url = 'http://repo1.maven.org/maven2')
      [base_url, @groupId.gsub('.', '/'), @artifactId, @version,
          @artifactId + '-' + @version + '.' + @packaging.to_s] * '/'
    end

    #https://repository.jboss.org/nexus/content/repositories/unzip/org/jboss/arquillian/core/arquillian-core-api/1.1.5.Final/arquillian-core-api-1.1.5.Final-javadoc.jar-unzip/index.html
    def to_javadoc_url(base_url = 'https://repository.jboss.org/nexus/content/repositories/unzip')
      [base_url, @groupId.gsub('.', '/'), @artifactId, @version,
        @artifactId + '-' + @version + '-javadoc' + '.' + @packaging.to_s + '-unzip/index.html'] * '/'
      end

    def to_pom_url(base_url = 'http://repo1.maven.org/maven2')
      [base_url, @groupId.gsub('.', '/'), @artifactId, @version,
          @artifactId + '-' + @version + '.' + @packaging.to_s].join('/').gsub(/\.jar$/, '.pom')
    end

    def to_relative
      [@artifactId, @packaging.to_s, @version] * ':'
    end

    def to_s
      [@groupId, @artifactId, @packaging.to_s, @version] * ':'
    end
  end
end

module Awestruct::Extensions::Repository::Visitors
  module Clone
    include Base

    def visit(repository, site)
      repos_dir = nil
      if site.repos_dir
        repos_dir = site.repos_dir
      else
        repos_dir = File.join(site.tmp_dir, 'repos')
      end
      if !File.directory? repos_dir
        FileUtils.mkdir_p(repos_dir)
      end
      clone_dir = File.join(repos_dir, repository.path)
      rc = nil
      if !File.directory? clone_dir
        puts "Cloning repository #{repository.clone_url} -> #{clone_dir}"
        rc = Git.clone(repository.clone_url, clone_dir)
        if repository.master_branch.nil?
          rc.checkout(repository.master_branch)
        else
          repository.master_branch = rc.current_branch
        end
      else
        puts "Using cloned repository #{clone_dir}"
        rc = Git.open(clone_dir)
        master_branch = repository.master_branch
        if master_branch.nil?
          master_branch = rc.branches.find{|b| !b.remote and  !(b.name.include? 'detached' or b.name.include? 'no branch')}.name
          repository.master_branch = master_branch
        end
        rc.checkout(master_branch)
        begin
          # attempt a light pull
          rc.pull('origin')
        rescue
          # do hard reset to master branch, some forced change might have occured upstream
          rc.fetch('origin')
          rc.reset_hard("origin/#{master_branch}")
        end
      end
      repository.clone_dir = clone_dir
      repository.client = rc
    end
  end

  module RepositoryHelpers
    # Retrieves the contributors between the two commits, filtering
    # by the relative path, if present
    def self.resolve_contributors_between(site, repository, sha1, sha2)
      range_author_index = {}
      RepositoryHelpers.resolve_commits_between(repository, sha1, sha2).map {|c|
        # we'll use email as the key to finding their identity; the sha we just need temporarily
        # clear out bogus characters from email and downcase
        OpenStruct.new({:name => c.author.name, :email => c.author.email.downcase.gsub(/[^\w@\.\(\)]/, ''), :commits => 0, :sha => c.sha})
      }.each {|e|
        # This loop both grabs unique authors by email and counts their commits
        if !range_author_index.has_key? e.email
          range_author_index[e.email] = e
        end
        range_author_index[e.email].commits += 1
      }

      range_author_index.values.each {|e|
        # this loop registers author in global index if not present
        if repository.host.eql? 'github.com'
          site.git_author_index[e.email] ||= OpenStruct.new({
            :email => e.email,
            :name => e.name,
            :sample_commit_sha => e.sha,
            :sample_commit_url => RepositoryHelpers.build_commit_url(repository, e.sha, 'json'),
            :commits => 0,
            :repositories => []
          })
          site.git_author_index[e.email].commits += e.commits
          site.git_author_index[e.email].repositories |= [repository.html_url]
        end
        e.delete_field('sha')
      }.sort {|a, b| a.name <=> b.name}
    end

    # Retrieves the commits in the range, filtered on the relative
    # path in the repository, if present
    def self.resolve_commits_between(repository, sha1, sha2)
      rc = repository.client
      log = rc.log(nil).path(repository.relative_path)
      if sha1.nil?
        log = log.object(sha2)
      else
        log = log.between(sha1, sha2)
      end
    end

    def self.build_commit_url(repository, sha, ext)
      if "html".eql? ext
        url = repository.html_url + '/commit/' + sha + '.' + ext
      elsif !repository.commits_url.nil?
        url = repository.commits_url.gsub(/\{.*/, "/#{sha}")
      else
        url = repository.html_url + '/commit/' + sha + '.' + ext
      end
      return url
    end
  end

  module MavenHelpers

    # Traverse all modules recursivly in a repository from a given rev:root
    def self.traverse_modules(rev, repository)
      pomrev = nil
      begin
        pomrev = repository.client.revparse("#{rev}pom.xml")
      rescue
        puts "info: missing pom.xml in #{rev}"
        return
      end
      pom = REXML::Document.new(repository.client.cat_file(pomrev))
      yield rev, pom

      unique_modules = Set.new
      pom.each_element('/project/modules/module') do |mod|
        unique_modules << mod.text()
      end
      pom.each_element('/project/profiles/profile/modules/module') do |mod|
        unique_modules << mod.text()
      end

      unique_modules.each do |submodule|
        MavenHelpers.traverse_modules("#{rev}#{submodule}/", repository) { |y, x| yield(y, x)}
      end
    end

    def self.to_relative_sub_path(rev, relative_repository_path)
      rev.gsub(/.*:/, '').gsub(relative_repository_path, '')
    end
  end

  # SEMI-HACK think about how best to curate & display info about these special repos
  # FIXME at least try to make GenericMavenComponent build on this one; perhaps a website component?
  module GenericComponent
    include Base
    def handles(repository)
      repository.path == 'arquillian-showcase' or
          !File.exist? File.join(repository.clone_dir, repository.relative_path, 'pom.xml')
    end

    def visit(repository, site)
      rc = repository.client
      c = OpenStruct.new({
        :repository => repository,
        :basepath => repository.path.eql?(repository.owner) ? repository.path : repository.path.sub(/^#{repository.owner}-/, ''),
        :owner => repository.owner,
        :name => repository.name,
        :desc => repository.desc,
        :contributors => []
      })
      # FIXME not dry (from below)!
      RepositoryHelpers.resolve_contributors_between(site, repository, nil, rc.revparse('HEAD')).each do |contrib|
        i = c.contributors.index {|n| n.email == contrib.email}
        if i.nil?
          c.contributors << contrib
        else
          c.contributors[i].commits += contrib.commits
        end
      end
    end
  end

  module GenericGradleComponent
    include Base

    def initialize
      @root_head_build = nil
    end

    def handles(repository)
      repository.path != 'arquillian-showcase' and
          File.exist? File.join(repository.clone_dir, repository.relative_path, 'build.gradle')
    end

    def visit(repository, site)
      rc = repository.client
      c = OpenStruct.new({
        :repository => repository,
        :basepath => repository.path.eql?(repository.owner) ? repository.path : repository.path.sub(/^#{repository.owner}-/, ''),
        :key => repository.path.split('-').last, # this is how components are matched in jira
        :owner => repository.owner,
        :html_url => repository.relative_path.empty? ? repository.html_url : "#{repository.html_url}/tree/#{repository.master_branch}/#{repository.relative_path.chomp('/')}",
        :external => !repository.owner =~ /arquillian|shrinkwrap/,
        :name => resolve_name(repository),
        :desc => repository.desc,
        :groupId => resolve_group_id(repository),
        :parent => true,
        :lead => resolve_current_lead(repository, site.component_leads),
        # we should not assume the license for external modules (hardcoding is not ideal either)
        :license => ['jbossas', 'wildfly' 'jsfunit'].include?(repository.owner) ? 'LGPL-2.1' : 'Apache-2.0',
        :releases => [],
        :contributors => []
      })
      prev_sha = nil
      rc.tags.select {|t|
          # supports formats: 1.0.0.Alpha1
          #t.name =~ /^[1-9]\d*\.\d+\.\d+\.((Alpha|Beta|CR)[1-9]\d*|Final)$/
          # supports formats: 1.0.0.Alpha1 or 1.0.0-alpha-1 or with prefix- or 1.0.0 or 0.1
          t.name =~ /^([a-z]+-?)?[0-9]\d*\.\d+(\.\d+)?([\.-]((alpha|beta|cr)-?[1-9]\d*|final))?$/i
      }.sort_by{|t| rc.gcommit(t).author_date}.each do |t|
        # skip tag if arquillian has nothing to do with it
        next if repository.relative_path and rc.log(1).object(t.name).path(repository.relative_path).size.zero?
        # for some reason, we have to use ^0 to get to the actual commit, can't use t.sha
        sha = rc.revparse(t.name + '^0')
        commit = rc.gcommit(sha)
        committer = commit.committer
        release = OpenStruct.new({
          :tag => t.name,
          :version => t.name.gsub(/^([a-z]+-?)/, ''),
          :key => (c.key.eql?('core') ? '' : c.key + '_') + t.name, # jira release version key, should we add owner?
          #:license => 'track?',
          :sha => sha,
          :html_url => RepositoryHelpers.build_commit_url(repository, sha, 'html'),
          :json_url => RepositoryHelpers.build_commit_url(repository, sha, 'json'),
          :date => commit.author_date,
          :released_by => OpenStruct.new({
            :name => committer.name,
            :email => committer.email.downcase
          }),
          :contributors => RepositoryHelpers.resolve_contributors_between(site, repository, prev_sha, sha),
          :published_artifacts => []
        })
        release.compiledeps = []
        c.releases << release
        prev_sha = sha
      end
      c.latest_version = (!c.releases.empty? ? c.releases.last.version : resolve_head_version(repository))
      c.latest_tag = (!c.releases.empty? ? c.releases.last.tag : 'HEAD')
      c.releases.each do |r|
        # FIXME not dry!
        r.contributors.each do |contrib|
          i = c.contributors.index {|n| n.email == contrib.email}
          if i.nil?
            c.contributors << contrib
          else
            c.contributors[i].commits += contrib.commits
          end
        end
      end

      # FIXME not dry!
      RepositoryHelpers.resolve_contributors_between(site, repository, prev_sha, rc.revparse('HEAD')).each do |contrib|
        i = c.contributors.index {|n| n.email == contrib.email}
        if i.nil?
          c.contributors << contrib
        else
          c.contributors[i].commits += contrib.commits
        end
      end

      # we can be pretty sure we'll have at least one commit, otherwise why the repository ;)
      last = rc.log(1).path(repository.relative_path).first
      c.last_commit = OpenStruct.new({
        :author => last.author,
        :date => last.date,
        :message => last.message,
        :sha => last.sha,
        :html_url => RepositoryHelpers.build_commit_url(repository, last.sha, 'html'),
        :json_url => RepositoryHelpers.build_commit_url(repository, last.sha, 'json')
      })
      c.unreleased_commits = RepositoryHelpers.resolve_commits_between(repository, prev_sha, rc.revparse('HEAD')).size

      c.modules = []
      site.components[repository.path] = c
    end

    def resolve_name(repository)
      build = load_root_head_build(repository)
      name = $1 if build =~ /root.+'name',.+'(.+)'/

      # FIXME note misspelling of Aggregator in Drone extension
      name.nil? ? repository.path : name.gsub(/[ :]*(Aggregator|Agreggator|Parent|module)+/, '')
    end

    def resolve_group_id(repository)
      build = load_root_head_build(repository)
      $1 if build =~ /group = '(.+arquillian.+)'/
    end

    def resolve_head_version(repository)
      build = load_root_head_build(repository)
      build.root.text('version')
    end

    def resolve_head_version(repository)
      build = load_root_head_build(repository)
      $1 if build =~ /version = '(.+)'/
    end

    def load_root_head_build(repository)
      @root_head_build ||= repository.client.cat_file(repository.client.revparse("HEAD:#{repository.relative_path}build.gradle"))
    end

    def resolve_current_lead(repository, component_leads)
      if !component_leads.nil? and component_leads.has_key? repository.path
        lead = component_leads[repository.path]
      else
        if lead.nil?
          # FIXME parameterize (keep in mind the JIRA extension hits most of the leads)
          if repository.path.eql? 'arquillian-gradle-plugin'
            lead = OpenStruct.new({
              :name => 'Benjamin Muschko',
              :github_id => 'bmuschko'
            })
          end
        end
        # update the global index (why not?)
        if !lead.nil?
          component_leads[repository.path] = lead
        end
      end
      lead
    end
  end

  module GenericMavenComponent
    include Base

    def initialize
      @root_head_pom = nil
    end

    def handles(repository)
      repository.path != 'arquillian-showcase' and
          repository.path != 'arquillian-container-reloaded' and
          File.exist? File.join(repository.clone_dir, repository.relative_path, 'pom.xml')
      #repository.path =~ /^arquillian-(core$|(testrunner|container|extension)-.+$)/ and
      #    repository.path != 'arquillian-testrunner-jbehave'
    end

    def visit(repository, site)
      @root_head_pom = nil
      rc = repository.client
      c = OpenStruct.new({
        :repository => repository,
        :basepath => repository.path.eql?(repository.owner) ? repository.path : repository.path.sub(/^#{repository.owner}-/, ''),
        :key => repository.path.split('-').last, # this is how components are matched in jira
        :owner => repository.owner,
        :html_url => repository.relative_path.empty? ? repository.html_url : "#{repository.html_url}/tree/#{repository.master_branch}/#{repository.relative_path.chomp('/')}",
        :external => !repository.owner =~ /arquillian|shrinkwrap/,
        :name => resolve_name(repository),
        :desc => repository.desc,
        :groupId => resolve_group_id(repository),
        :parent => true,
        :lead => resolve_current_lead(repository, site.component_leads),
        # we should not assume the license for external modules (hardcoding is not ideal either)
        :license => ['jbossas', 'wildfly' 'jsfunit'].include?(repository.owner) ? 'LGPL-2.1' : 'Apache-2.0',
        :releases => [],
        :contributors => []
      })
      prev_sha = nil
      rc.tags.select {|t|
          # supports formats: 1.0.0.Alpha1
          #t.name =~ /^[1-9]\d*\.\d+\.\d+\.((Alpha|Beta|CR)[1-9]\d*|Final)$/
          # supports formats: 1.0.0.Alpha1 or 1.0.0-alpha-1 or with prefix- or 1.0.0 or 0.1
          t.name =~ /^([a-z]+-?)?[0-9]\d*\.\d+(\.\d+)?([\.-]((alpha|beta|cr)-?[1-9]\d*|final))?$/i
      }.sort_by{|t| rc.gcommit(t).author_date}.each do |t|
        # skip tag if arquillian has nothing to do with it
        next if repository.relative_path and rc.log(1).object(t.name).path(repository.relative_path).size.zero?
        # for some reason, we have to use ^0 to get to the actual commit, can't use t.sha
        sha = rc.revparse(t.name + '^0')
        commit = rc.gcommit(sha)
        committer = commit.committer
        release = OpenStruct.new({
          :tag => t.name,
          :version => t.name.gsub(/^([a-z]+-?)/, ''),
          :key => (c.key.eql?('core') ? '' : c.key + '_') + t.name, # jira release version key, should we add owner?
          #:license => 'track?',
          :sha => sha,
          :html_url => RepositoryHelpers.build_commit_url(repository, sha, 'html'),
          :json_url => RepositoryHelpers.build_commit_url(repository, sha, 'json'),
          :date => commit.author_date,
          :released_by => OpenStruct.new({
            :name => committer.name,
            :email => committer.email.downcase
          }),
          :contributors => RepositoryHelpers.resolve_contributors_between(site, repository, prev_sha, sha),
          :published_artifacts => []
        })
        if site.resolve_published_artifacts and repository.owner =~ /arquillian|shrinkwrap/
          resolve_published_artifacts(site.dir, repository, release)
        end
        # not assigning to release for now since it can be very space intensive
        #if site.release_notes_by_version.has_key? release.key
        #  release.issues = site.release_notes_by_version[release.key]
        #end
        depversions = resolve_dep_versions(repository, release.tag)
        release.compiledeps = []
        {
          'arquillian' => 'Arquillian Core',
          'arquillian_core' => 'Arquillian Core',
          'jboss_arquillian_core' => 'Arquillian Core',
          'org_jboss_arquillian' => 'Arquillian Core',
          'org_jboss_arquillian_core' => 'Arquillian Core',
          'arquillian_cube' => 'Arquillian Cube Extension',
          'arquillian_drone' => 'Arquillian Drone Extension',
          'arquillian_warp' => 'Arquillian Warp',
          'arquillian_graphene' => 'Graphene',
          'org_jboss_arquillian_graphene' => 'Graphene',
          'arquillian_transaction' => 'Arquillian Transaction Extension',
          'arquillian_persistence' => 'Arquillian Persistence Extension',
          'arquillian_spring' => 'Arquillian Spring Framework Extension',
          'arquillian_byteman' => 'Arquillian Extension Byteman',
          'arquillian_jacoco' => 'Arquillian Extension Jacoco',
          'arquillian_recorder' => 'Arquillian Recorder',
          'recorder' => 'Arquillian Recorder',
          'arquillian_governor' => 'Arquillian Governor',
          'arquillian_rest' => 'Arquillian REST Extension',
          'arquillian_spacelift' => 'Arquillian Spacelift',
          'arquillian_chameleon' => 'Arquillian Container Chameleon',
          'arquillian_cukes_in_space' => 'Arquillian Cukes in Space',
          'shrinkwrap_shrinkwrap' => 'ShrinkWrap',
          'jboss_shrinkwrap' => 'ShrinkWrap',
          'shrinkwrap' => 'ShrinkWrap',
          'shrinkwrap_descriptors' => 'ShrinkWrap Descriptors',
          'shrinkwrap_descriptor' => 'ShrinkWrap Descriptors',
          'shrinkwrap_resolver' => 'ShrinkWrap Resolver',
          'shrinkwrap_resolvers' => 'ShrinkWrap Resolver',
          'selenium' => 'Selenium',
          'junit_junit' => 'JUnit',
          'testng_testng' => 'TestNG',
          'spock' => 'Spock',
          'selendroid' => 'Selendroid'
        }.each do |key, name|
          if depversions.has_key? key
            depversion = depversions[key]
            if(depversion.include?("${"))
              if depversion =~ /\$\{version\.(.*)\}/
                depversion = depversions[$1]
              end
            end
            release.compiledeps << OpenStruct.new({:name => name, :key => key, :version => depversion})
          end
        end
        c.releases << release
        prev_sha = sha
      end
      c.latest_version = (!c.releases.empty? ? c.releases.last.version : resolve_head_version(repository))
      c.latest_tag = (!c.releases.empty? ? c.releases.last.tag : 'HEAD')
      c.releases.each do |r|
        # FIXME not dry!
        r.contributors.each do |contrib|
          i = c.contributors.index {|n| n.email == contrib.email}
          if i.nil?
            c.contributors << contrib
          else
            c.contributors[i].commits += contrib.commits
          end
        end
      end
      # FIXME not dry!
      RepositoryHelpers.resolve_contributors_between(site, repository, prev_sha, rc.revparse('HEAD')).each do |contrib|
        i = c.contributors.index {|n| n.email == contrib.email}
        if i.nil?
          c.contributors << contrib
        else
          c.contributors[i].commits += contrib.commits
        end
      end

      # we can be pretty sure we'll have at least one commit, otherwise why the repository ;)
      last = rc.log(1).path(repository.relative_path).first
      c.last_commit = OpenStruct.new({
        :author => last.author,
        :date => last.date,
        :message => last.message,
        :sha => last.sha,
        :html_url => RepositoryHelpers.build_commit_url(repository, last.sha, 'html'),
        :json_url => RepositoryHelpers.build_commit_url(repository, last.sha, 'json')
      })
      c.unreleased_commits = RepositoryHelpers.resolve_commits_between(repository, prev_sha, rc.revparse('HEAD')).size

      c.modules = []
      site.components[repository.path] = c
    end

    def resolve_name(repository)
      pom = load_root_head_pom(repository)
      name = pom.root.text('name')
      # FIXME note misspelling of Aggregator in Drone extension
      name.nil? ? repository.path : name.gsub(/[ :]*(Aggregator|Agreggator|Parent|module|and Build)+/, '').strip
    end

    def resolve_group_id(repository)
      pom = load_root_head_pom(repository)
      pom.root.text('groupId') || pom.root.elements['parent'].text('groupId')
    end

    def resolve_head_version(repository)
      pom = load_root_head_pom(repository)
      pom.root.text('version')
    end

    # QUESTION should we track lead by release version? (for historical reasons)
    def resolve_current_lead(repository, component_leads)
      if !component_leads.nil? and component_leads.has_key? repository.path
        lead = component_leads[repository.path]
      else
        lead = nil
        pom = load_root_head_pom(repository)
        pom.each_element('/project/developers/developer') do |dev|
          # capture first developer as fallback lead
          if lead.nil? and !dev.text('email').nil?
            lead = OpenStruct.new({:name => dev.text('name'), :email => dev.text('email').downcase})
          end

          if !dev.elements['roles'].nil?
            if !dev.elements['roles'].elements.find { |role| role.name.eql? 'role' and role.text =~ / Lead/ }.nil?
              lead = OpenStruct.new({:name => dev.text('name'), :email => dev.text('email').downcase})
              break
            end
          end
        end
        if lead.nil?
          # FIXME parameterize (keep in mind the JIRA extension hits most of the leads)
          if repository.path.eql? 'jboss-as' or repository.path.eql? 'wildfly'
            lead = OpenStruct.new({
              :name => 'Jason T. Greene',
              :jboss_username => 'jason.greene'
            })
          elsif repository.path.eql? 'plugin-arquillian'
            lead = OpenStruct.new({
              :name => 'Paul Bakker',
              :jboss_username => 'pbakker'
            })
          elsif repository.path.eql? 'arquillian-graphene'
            lead = OpenStruct.new({
              :name => 'Lukáš Fryč',
              :jboss_username => 'lfryc'
            })
          elsif repository.path.eql? 'arquillian-cube'
            lead = OpenStruct.new({
              :name => 'Alex Soto',
              :jboss_username => 'lordofthejars'
            })
          elsif repository.owner.eql? 'arquillian'
            lead = OpenStruct.new({
              :name => 'Aslak Knutsen',
              :jboss_username => 'aslak'
            })
          elsif repository.path.eql? 'resolver'
            lead = OpenStruct.new({
              :name => 'Karel Piwko',
              :jboss_username => 'kpiwko'
            })
          elsif repository.path.eql? 'descriptors-docker'
            lead = OpenStruct.new({
              :name => 'George Gastaldi',
              :jboss_username => 'gastaldi'
            })
          elsif repository.path.eql? 'shrinkwrap-osgi'
            lead = OpenStruct.new({
              :name => 'Carlos Sierra Andrés',
              :jboss_username => 'csierra'
            })
          elsif repository.owner.eql? 'shrinkwrap'
            lead = OpenStruct.new({
              :name => 'Andrew Lee Rubinger',
              :jboss_username => 'alrubinger'
            })
          elsif repository.path.eql? 'tomee'
            lead = OpenStruct.new({
              :name => 'David Blevins',
              :jboss_username => 'dblevins'
            })
          end
        end
        # update the global index (why not?)
        if !lead.nil?
          component_leads[repository.path] = lead
        end
      end
      lead
    end

    def resolve_dep_versions(repository, rev)
      rc = repository.client
      versions = {}
      # FIXME Android extension defines versions in android-bom/pom.xml
      ['pom.xml', 'build/pom.xml', 'android-bom/pom.xml', "#{repository.relative_path}pom.xml"].each do |path|
        # skip if path is not present in this revision
        #next if rc.log(1).object(rev).path(path).size.zero?
        pom_content = nil
        begin
          pom_content = rc.cat_file(rc.revparse("#{rev}:#{path}"))
        rescue
          next
        end
        pom = REXML::Document.new(pom_content)
        pom.each_element('/project/properties/*') do |prop|
          if (prop.name.start_with? 'version.' or prop.name.end_with? '.version') and
              not prop.name =~ /[\._]plugin$/ and
              not prop.name =~ /\.maven[\._]/
            versions[prop.name.sub(/\.?version\.?/, '').gsub('.', '_')] = prop.text
          end
        end
      end
      versions
    end

    def resolve_published_artifacts(sitedir, repository, release)
      parent_path = "#{release.tag}:#{repository.relative_path}"
      begin
        repository.client.revparse "#{parent_path}pom.xml"
      rescue
        return
      end

      MavenHelpers.traverse_modules(parent_path, repository) do |pathrev, pom|
        groupId = pom.root.text('groupId') || pom.root.elements['parent'].text('groupId')
        artifactId = pom.root.text('artifactId')
        #puts "#{artifactId} -> #{version}"
        packaging = (pom.root.text('packaging') || "jar").to_sym

        unless (groupId.eql? 'org.arquillian.universe' and !(artifactId.eql? 'arquillian-universe-parent')) or artifactId.eql? 'arquillian-universe'
          next unless artifactId =~ /.*bom|.*depchain.*|graphene/ or packaging == :jar
          next if artifactId =~/.*(ftest.*|inttest|example.*|build|build-config|build-resources)/
          next unless packaging == :pom || :jar
        end

        version = pom.root.text('version') || pom.root.elements['parent'].text('version')
        release.published_artifacts ||= []
        release.published_artifacts << Artifact::Coordinates.new(groupId, artifactId, packaging, version)
      end
    end

    def load_root_head_pom(repository)
      @root_head_pom ||= REXML::Document.new(
        repository.client.cat_file(repository.client.revparse("HEAD:#{repository.relative_path}pom.xml"))
      )
    end
  end

  # Combine Bom and Depchain in a common /scan Maven tree Base
  module BomModule
    include Base

    def handles(repository)
      true # we don't know until we do a full scan
    end

    def visit(repository, site)
      c = site.components[repository.path]
      if !c.nil? and !c.releases.nil? and c.releases.size > 0
        rev = c.latest_tag
        parent_path = "#{rev}:#{repository.relative_path}"
        # verify pom exists in this rev
        begin
          repository.client.revparse "#{parent_path}pom.xml"
        rescue
          return
        end
        MavenHelpers.traverse_modules(parent_path, repository) do |pathrev, pom|
          artifactId = pom.text('/project/artifactId')
          unless artifactId.index('bom').nil?
            name = pom.root.text('name')
            groupId = pom.root.text('groupId') || pom.root.elements['parent'].text('groupId')
            c.bom = Artifact::Coordinates.new(groupId, artifactId, :pom, c.latest_version)
            return
          end
        end
      end
    end
  end

  module DepchainModule
    include Base

    def handles(repository)
      true # we don't know until we do a full scan
    end

    def visit(repository, site)
      c = site.components[repository.path]
      if !c.nil? and !c.releases.nil? and c.releases.size > 0
        rev = c.latest_tag
        parent_path = "#{rev}:#{repository.relative_path}"
        # verify pom exists in this rev
        begin
          repository.client.revparse "#{parent_path}pom.xml"
        rescue
          return
        end
        MavenHelpers.traverse_modules(parent_path, repository) do |pathrev, pom|
          artifactId = pom.text('/project/artifactId')
          name = pom.text('/project/name')
          #puts "#{name.to_s} > #{artifactId}"
          # Some have x-module-depchain and others x-module
          unless "#{artifactId} #{name.to_s.downcase}".index('depchain').nil?
            name = pom.root.text('name')
            packaging = pom.root.text('packaging') || "pom"
            groupId = pom.root.text('groupId') || pom.root.elements['parent'].text('groupId')
            c.depchains ||= []
            c.depchains << Artifact.new(name, Artifact::Coordinates.new(groupId, artifactId, packaging.to_sym, c.latest_version))
          end
        end
      end
    end
  end

  module PlatformComponent
    include Base

    def handles(repository)
      repository.path == 'arquillian-core'
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'platform'
      c.type_name = c.type.humanize.titleize
      c.name = 'Arquillian Core'
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        :basepath => c.repository.path.match(/-([^-]+)$/)[1],
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      m.artifacts = [
        Artifact.new('JUnit Integration',
            Artifact::Coordinates.new('org.jboss.arquillian.junit', 'arquillian-junit-container')),
        Artifact.new('TestNG Integration',
            Artifact::Coordinates.new('org.jboss.arquillian.testng', 'arquillian-testng-container'))
      ]
      c.modules << m
      site.modules[c.type] << m
    end

  end

  module TestRunnerComponent
    include Base

    def handles(repository)
      repository.path =~ /^arquillian\-testrunner\-.+/ and
          File.exist? File.join(repository.clone_dir, 'pom.xml')
      #repository.path =~ /^arquillian\-testrunner\-.+/ and
      #    repository.path != 'arquillian-testrunner-jbehave'
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'test_runner'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        :basepath => c.repository.path.match(/-([^-]+)$/)[1],
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      c.modules << m
      site.modules[c.type] << m
    end
  end

  module ContainerComponent
    include Base

    def handles(repository)
      repository.path != 'arquillian-container-reloaded' and
          (repository.path =~ /^arquillian\-container\-.+/ or ['jboss-as', 'wildfly-arquillian', 'tomee'].include? repository.path)
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'container_adapter'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      containers = resolve_container_adapters(repository, c)
      containers.each do |container|
        container.component = c
        populate_container_info(repository, c, container)
        # temporary hack!!! can we make this more generic?
        c.family = container.vendor
        c.modules << container
        site.modules[c.type] << container
      end
    end

    # NOTE we are not showing container adapters that are only in SNAPSHOT unless the
    # component (repository) itself has not yet been released
    def resolve_container_adapters(repository, component)
      vendor = (repository.path == 'jboss-as' ? 'jbossas7' : repository.path.match(/([^-]+)$/)[1])
      vendor = 'wildfly' if repository.path == 'wildfly-arquillian'
      adapters = []
      rev = component.latest_tag
      rev = "HEAD" unless rev
      begin
        pomrev = repository.client.revparse("#{rev}:#{repository.relative_path}pom.xml")
      rescue
        puts "Using HEAD for #{repository.path} since repository structure is inconsistent at #{rev}"
        rev = "HEAD"
        pomrev = repository.client.revparse("#{rev}:#{repository.relative_path}pom.xml")
      end
      module_cnt = 0
      parent_path = ("#{rev}:#{repository.relative_path}")
      MavenHelpers.traverse_modules(parent_path, repository) do |pathrev, pom|
        mod = pom.text('/project/artifactId')
        # Need a sub extension point to clean up for specific containers
        if mod =~ /.*?-(remote|managed|embedded)(-(.+))?$/ and !(repository.path == 'tomee' and mod =~ /.*webapp.*/)
          (management, min_version) = [$1, $3]
          module_cnt += 1
          adapters << OpenStruct.new({
            :relative_path => MavenHelpers.to_relative_sub_path(pathrev, repository.relative_path),
            :basepath => mod.sub(/container(?=-)/, vendor),
            :vendor => vendor,
            :management => management,
            :min_version => min_version
          })
        elsif mod.eql? 'arquillian-openshift' or
          (mod.eql? 'arquillian-openshift-express' and !(pom.text('/project/name') =~ /.*Relocation/)) or
          mod.eql? 'arquillian-cloudbees'
          # FIXME this should be openshift-remote
          module_cnt += 1
          adapters << OpenStruct.new({
            :relative_path => MavenHelpers.to_relative_sub_path(pathrev, repository.relative_path),
            :basepath => mod,
            :vendor => vendor,
            :management => 'remote'
          })
        elsif mod.eql? 'arquillian-container-chameleon'
          # FIXME this should be openshift-remote
          module_cnt += 1
          adapters << OpenStruct.new({
            :relative_path => MavenHelpers.to_relative_sub_path(pathrev, repository.relative_path),
            :basepath => mod,
            :vendor => vendor,
            :management => 'any'
          })
        end
      end
      adapters
    end

    def populate_container_info(repository, component, container)
      rc = repository.client
      container.enrichers = []
      rev = component.latest_tag
      pomrev = nil
      begin
        pomrev = repository.client.revparse("#{rev}:#{repository.relative_path}#{container.relative_path}pom.xml")
      rescue
        puts "Using HEAD for #{repository.path} since repository structure is inconsistent at #{rev}"
        pomrev = repository.client.revparse("HEAD:#{repository.relative_path}#{container.relative_path}pom.xml")
      end
      pom = REXML::Document.new(rc.cat_file(pomrev))
      container.name = pom.root.text('name').sub(/ Container$/, '\0 Adapter').sub(/^Arquillian Container (.*)/, 'Arquillian \1 Container Adapter')
      if repository.path == 'jboss-as'
        container.name = container.name.sub(/.*Arquillian /, 'Arquillian JBoss AS 7 ')
      elsif repository.path == 'wildfly-arquillian'
        container.name = container.name.sub(/.*Arquillian /, 'Arquillian WildFly ')
      elsif repository.path == 'tomee'
        container.name = container.name.sub(/.*:: /, 'Arquillian TomEE ')
        container.name = container.name.sub(/ Adaptor/, ' Container Adapter')
        container.name = container.name + ' Container Adapter' unless container.name =~/Adapter/
        container.name = container.name.sub(/TomEE /, '') if container.name =~/openejb/i
      end
      container.desc = pom.root.text('description') || '!!!Missing description!!!'
      container.artifacts = [
        Artifact.new(container.name,
            Artifact::Coordinates.new(component.groupId, pom.root.text('artifactId'), :jar, component.latest_version))
      ]
      # FIXME also need to check common submodule
      pom.each_element('/project/dependencies/dependency') do |dep|
        if dep.text('groupId').eql? 'org.jboss.arquillian.testenricher'
          container.enrichers << dep.text('artifactId').sub(/^arquillian-testenricher-/, '')
        elsif dep.text('groupId').eql? 'org.jboss.arquillian.protocol'
          container.protocol = dep.text('artifactId').sub(/^arquillian-protocol-/, '')
        end
      end
    end
  end

  module ExtensionComponent
    include Base

    def handles(repository)
      repository.path =~ /^(arquillian-extension-.+|jsfunit|arquillian-graphene|arquillian-droidium|arquillian-spacelift|arquillian-recorder|arquillian-cube|arquillian-governor)$/
    end

    def visit(repository, site)
      c = site.components[repository.path]
      return if c.nil?
      c.type = 'extension'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        #:basepath => c.repository.path.match(/-([^-]+)$/)[1],
        :basepath => c.repository.path.split('-').last,
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      resolve_extension_artifacts(repository, m)
      c.modules << m
      site.modules[c.type] << m
    end

    def resolve_extension_artifacts(repository, mod)
      return unless mod.component.depchains.nil?
      rc = repository.client
      rev = mod.component.latest_tag
      pom = REXML::Document.new(rc.cat_file(rc.revparse("#{rev}:#{repository.relative_path}pom.xml")))
      count = 0
      primary = nil
      pom.each_element('/project/modules/module') do |m|
        count += 1
        if (m.text.eql? 'impl' or m.text.end_with? '-impl')
          primary = m.text
        end
      end
      primarypom = nil
      if count.zero?
        primarypom = pom
      elsif !primary.nil?
        primarypom = REXML::Document.new(rc.cat_file(rc.revparse("#{rev}:#{repository.relative_path}#{primary}/pom.xml")))
      elsif !count.zero? and primary.nil?
        pom.each_element('/project/modules/module') do |m|
          unless m.text.eql? 'impl' or m.text.end_with? '-impl' or m.text.end_with? 'api' or m.text.end_with? 'spi' or m.text.include? 'ftest'
            primarypom = REXML::Document.new(rc.cat_file(rc.revparse("#{rev}:#{repository.relative_path}#{m.text}/pom.xml")))
          end
        end
      end
      if !primarypom.nil?
        artifactId = primarypom.root.text('artifactId')
        name = primarypom.root.text('name')
        packaging = primarypom.root.text('packaging')
        groupId = pom.root.text('groupId') || pom.root.elements['parent'].text('groupId')
        mod.artifacts = [
          Artifact.new(name, Artifact::Coordinates.new(groupId, artifactId, packaging, mod.component.latest_version))
        ]
      end
    end
  end

  # QUICK HACK! -> too much boilerplate
  module PluginComponent
    include Base

    def handles(repository)
      repository.path =~ /^(arquillian-maven|plugin-arquillian|arquillian-gradle-plugin)$/
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'tool_plugin'
      c.type_name = c.type.humanize.titleize
      # temporarily fix broken name
      c.name = 'Arquillian Forge Plugin' if c.name.eql? 'plugin-arquillian'
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        :basepath => c.repository.path,
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      c.modules << m
      site.modules[c.type] << m
    end
  end

  module ShrinkWrapComponent
    include Base

    def handles(repository)
      repository.owner.eql? 'shrinkwrap'
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'shrinkwrap'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        :basepath => c.repository.path,
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      c.modules << m
      site.modules[c.type] << m
    end
  end

  module ArquillianTCKComponent
    include Base

    def handles(repository)
      repository.path =~ /^(arquillian-tck)$/
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'tck'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      m = OpenStruct.new({
        :basepath => c.repository.path,
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      c.modules << m
      site.modules[c.type] << m
    end
  end

  module ArquillianUniverseComponent
    include Base

    def handles(repository)
      repository.path =~ /^(arquillian-universe-bom)$/
    end

    def visit(repository, site)
      c = site.components[repository.path]
      c.type = 'bom'
      c.type_name = c.type.humanize.titleize
      if site.modules[c.type].nil?
        site.modules[c.type] = []
      end
      c.extensions = populate_modules(repository, 'master')
      m = OpenStruct.new({
        :basepath => c.repository.path,
        :name => c.name,
        :desc => c.desc,
        :component => c
      })
      c.modules << m
      site.modules[c.type] << m
    end

    def populate_modules(repository, rev)
      modules = []
      rc = repository.client
      pomrev = repository.client.revparse("#{rev}:#{repository.relative_path}arquillian-universe/pom.xml")
      pom = REXML::Document.new(rc.cat_file(pomrev))
      pom.each_element('/project/dependencyManagement/dependencies/*') do |dep_mod|
        group_id = dep_mod.text('groupId')
        artifact_id = dep_mod.text('artifactId')

        modrev = repository.client.revparse("#{rev}:#{repository.relative_path}#{artifact_id}/pom.xml")
        modpom = REXML::Document.new(rc.cat_file(modrev))
        name = modpom.text("/project/name")

        modules.push OpenStruct.new({
          :name => name,
          :groupId => group_id,
          :artifactId => artifact_id})
      end

      return modules
    end
  end
end
