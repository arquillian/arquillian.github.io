# -*- encoding : utf-8 -*-
require_relative 'restclient_extensions'
require 'rexml/document'
require 'uri'
require 'json'
require 'parallel'

module Awestruct
  module Extensions
    module Repository

      # FIXME make collectors modular, so that we register an api.github collector for instance
      class Collector
        def initialize(opts = {})
          @repositories = []
          @use_data_cache = opts[:use_data_cache] || true
          @observers = opts[:observers] || []
        end

        def execute(site)

          more_pages = true
          page = 1
          while more_pages do

            url = "https://api.github.com/orgs/arquillian/repos?page=#{page}"
            cache_key = "github/repos-#{page}.xml"
            begin
              # expire after 3 days
              resp = RestClient.get url, :accept => 'application/json',
                                    :cache_key => cache_key, :cache_expiry_age => 86400 * 3
            rescue Exception => e
              puts "Unable to crawl #{url}. Reason: #{e.message}"
              break
            end

            doc = JSON.parse(resp.gsub('\"', '"').gsub('"[', '[').gsub(']"', ']'))

            if doc.count == 0
              more_pages = false
              break
            end
            page += 1

            doc.each do |e|
              #omitted repositories:
              # - without any commit or almost empty (arquillian-sandbox and arquillian-container-gae)
              # - not too interesting with high amount of releases (selenium-bom)
              # - deprecated repository (arquillian_deprecated)
              # - non-maven repository (arquillian-container-jruby)
              unless e['pushed_at'].nil? || e['name'] == "arquillian-selenium-bom" || e['name'] == "arquillian-container-gae" || e['name'] == "arquillian_deprecated" || e['name'] == "arquillian-container-jruby"

                git_url = e['git_url']
                repository = OpenStruct.new({
                                                :path => e['name'],
                                                :relative_path => '',
                                                :desc => nil,
                                                :owner => e['owner']['login'],
                                                :host => URI(git_url).host,
                                                :type => 'git',
                                                :commits_url => e['commits_url'],
                                                :html_url => e['html_url'],
                                                :clone_url => git_url
                                            })
                @repositories << repository
              end
            end
          end

          @repositories << OpenStruct.new(
              :path => 'shrinkwrap',
              :desc => nil,
              :relative_path => '',
              :owner => 'shrinkwrap',
              :host => 'github.com',
              :type => 'git',
              :commits_url => 'https://api.github.com/repos/shrinkwrap/shrinkwrap/commits{/sha}',
              :html_url => 'https://github.com/shrinkwrap/shrinkwrap',
              :clone_url => 'git://github.com/shrinkwrap/shrinkwrap.git'
          )
          @repositories << OpenStruct.new(
              :path => 'resolver',
              :desc => nil,
              :relative_path => '',
              :owner => 'shrinkwrap',
              :host => 'github.com',
              :type => 'git',
              :commits_url => 'https://api.github.com/repos/shrinkwrap/resolver/commits{/sha}',
              :html_url => 'https://github.com/shrinkwrap/resolver',
              :clone_url => 'git://github.com/shrinkwrap/resolver.git'
          )
          @repositories << OpenStruct.new(
              :path => 'descriptors',
              :desc => nil,
              :relative_path => '',
              :owner => 'shrinkwrap',
              :host => 'github.com',
              :type => 'git',
              :commits_url => 'https://api.github.com/repos/shrinkwrap/descriptors/commits{/sha}',
              :html_url => 'https://github.com/shrinkwrap/descriptors',
              :clone_url => 'git://github.com/shrinkwrap/descriptors.git'
          )
          @repositories << OpenStruct.new(
              :path => 'descriptors-docker',
              :desc => nil,
              :relative_path => '',
              :owner => 'shrinkwrap',
              :host => 'github.com',
              :type => 'git',
              :commits_url => 'https://api.github.com/repos/shrinkwrap/descriptors-docker/commits{/sha}',
              :html_url => 'https://github.com/shrinkwrap/descriptors-docker',
              :clone_url => 'git://github.com/shrinkwrap/descriptors-docker.git'
          )
          @repositories << OpenStruct.new(
              :path => 'shrinkwrap-osgi',
              :desc => nil,
              :relative_path => '',
              :owner => 'shrinkwrap',
              :host => 'github.com',
              :type => 'git',
              :commits_url => 'https://api.github.com/repos/shrinkwrap/shrinkwrap-osgi/commits{/sha}',
              :html_url => 'https://github.com/shrinkwrap/shrinkwrap-osgi',
              :clone_url => 'git://github.com/shrinkwrap/shrinkwrap-osgi.git'
          )

          @repositories << OpenStruct.new(
              :path => 'wildfly-arquillian',
              :desc => nil,
              :relative_path => '',
              :owner => 'wildfly',
              :host => 'github.com',
              :type => 'git',
              :html_url => 'https://github.com/wildfly/wildfly-arquillian',
              :clone_url => 'git://github.com/wildfly/wildfly-arquillian.git'
          )

          @repositories << OpenStruct.new(
              :path => 'tomee',
              :desc => nil,
              :relative_path => 'arquillian/',
              :owner => 'apache',
              :host => 'github.com',
              :type => 'git',
              :html_url => 'https://github.com/apache/tomee',
              :clone_url => 'git://github.com/apache/tomee.git'
          )

          @repositories << OpenStruct.new(
              :path => 'jsfunit',
              :desc => nil,
              :relative_path => 'jboss-jsfunit-arquillian/',
              :owner => 'jsfunit',
              :host => 'github.com',
              :type => 'git',
              :html_url => 'https://github.com/jsfunit/jsfunit',
              :clone_url => 'git://github.com/jsfunit/jsfunit.git'
          )

          @repositories << OpenStruct.new(
              :path => 'plugin-arquillian',
              :desc => nil,
              :relative_path => '',
              :owner => 'forge',
              :host => 'github.com',
              :type => 'git',
              :html_url => 'https://github.com/forge/plugin-arquillian',
              :clone_url => 'git://github.com/forge/plugin-arquillian.git'
          )

          @repositories.sort! { |a, b| a.path <=> b.path }
          # get the description for each github repository
          # TODO this may need review for efficiency
          @repositories.map { |r|
            r.owner if r.host == 'github.com'
          }.uniq.each { |org_name|
            more_pages = true
            org_url = "https://api.github.com/orgs/#{org_name}/repos"
            while more_pages
              org_repos_data = RestClient.get org_url, :accept => 'application/json'
              @repositories.each { |r|
                repo_data = org_repos_data.content.select { |c| r.clone_url.eql? c['git_url'] }
                if repo_data.size == 1
                  r.desc = repo_data.first['description']
                  r.commits_url = repo_data.first['commits_url']
                end
              }
              if org_repos_data.headers[:link] =~ /<(.*)>.+rel="next",/
                org_url = $1
              else
                more_pages = false
              end
            end

          }

          all_components = {}
          all_modules = {}

          site.git_author_index = {}
          @repositories.each do |r|

            # Store and Rest site state
            site.components = {}
            site.modules = {}

            repo_cache_file = File.join(site.tmp_dir, 'datacache', "#{r.path}.yml")
            if File.exist? repo_cache_file and @use_data_cache
              (site.components, site.modules) = YAML.load_file(repo_cache_file)
            else
              if r.host.eql? 'github.com'
                @observers.each do |o|
                  o.add_repository(r) if o.respond_to? 'add_repository'
                end
              end
              Visitors.defined.each do |v|
                if v.handles(r)
                  start_time = Time.now
                  v.visit(r, site)
                  puts "#{r.path.ljust(40)} #{v.name.split('::').last.rjust(25)} => #{'%.3f' % (Time.now - start_time)}"
                end
              end

              if @use_data_cache
                FileUtils.mkdir_p File.dirname repo_cache_file
                File.open(repo_cache_file, 'w:UTF-8') do |out|
                  site.components.each_pair { |_, c| c.repository.delete_field('client') }
                  YAML.dump([site.components, site.modules], out)
                end
              end
            end
            all_components.merge!(site.components || {})
            site.modules.each_pair do |k, v|
              all_modules[k] = [] unless all_modules[k]
              all_modules[k].concat(v)
            end

          end

          site.components = all_components
          site.modules = all_modules

          puts "Loaded #{site.components.size} components and #{site.modules.size} modules"

          rekeyed_index = find_author_email(site.git_author_index, site.github_mapping)

          # QUESTION should we do this in identities/github.rb?
          # FALLBACK ONLY BLOCK
          rekeyed_index.keys.each do |id|
            author = rekeyed_index[id]
            next unless author.github_id.nil?
            # an unmatched account will have exactly one e-mail
            unmatched_email = author.emails.first
            unmatched_name = author.name
            # attempt a name match (somewhat weak, but it will have to do)
            match = rekeyed_index.values.find { |candidate|
              !candidate.equal? author and candidate.name.downcase.eql? unmatched_name.downcase
            }
            if match
              # this is rather crude, but put the e-mail w/ lots of commits first
              if author.commits > match.commits
                match.emails.unshift unmatched_email
              else
                match.emails << unmatched_email
              end
              match.commits += author.commits
              match.repositories |= author.repositories
              rekeyed_index.delete id
              puts "Merged #{unmatched_name} <#{unmatched_email}> with matched github id #{match.github_id} based on name"
            else
              puts "Could not resolve github id for author #{unmatched_name} <#{unmatched_email}>"
            end
          end

          @observers.each do |o|
            o.add_match_filter(rekeyed_index) if o.respond_to? 'add_match_filter'
          end

        end

        def find_author_email(git_author_index, github_mapping)
          rekeyed_index = {}

          git_author_index.each do |email, author|
            commit_data = RestClient.get(author.sample_commit_url, :accept => 'application/json').content

            github_id = commit_data['commit']['author']['login']
            unless commit_data['author'].nil?
              github_id = commit_data['author']['login'] if github_id.nil? or github_id.empty?
            end

            github_id = github_id.to_s.downcase unless github_id.nil?
            github_id = "" if github_id.nil?

            if github_id.empty?
              match = github_mapping.find{|candidate| candidate.email.eql? author.email}
              if match
                github_id = match.github_id
                puts "Used github mapping to lookup github_id #{github_id} for #{author.name} <#{author.email}>"
              end
            end
            if not github_id.empty?
              author.github_id = github_id
              # github_id may exist in index when a person has multiple e-mail address w/ one github account
              if rekeyed_index.has_key? github_id
                entry = rekeyed_index[github_id]
                # this is rather crude, but put the e-mail w/ lots of commits first
                if author.commits > entry.commits
                  entry.emails.unshift author.email
                else
                  entry.emails << author.email
                end
                entry.commits += author.commits
                entry.repositories |= author.repositories
              else
                author.emails = [author.email]
                author.delete_field('email')
                rekeyed_index[github_id] = author
              end
            else
              author.github_id = nil
              author.emails = [author.email]
              author.delete_field('email')
              rekeyed_index[email] = author
            end
          end
          rekeyed_index
        end

      end

      module Visitors
        # @return array of visitors
        def self.defined
          @defined ||= []
        end

        module Base
          def self.included(base)
            base.extend(base)
            Visitors.defined << base
          end

          def handles(repository)
            true
          end

          # TODO could allow return false to halt processing of visitors
          def visit(repository, site)
            raise Error.new("#{self.inspect}#visit not defined!")
          end
        end
      end

    end
  end
end
