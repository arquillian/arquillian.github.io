require_relative 'restclient_extensions'
require 'rexml/document'
require 'uri'

module Awestruct
  module Extensions
    module Repository

      # FIXME make collectors modular, so that we register an ohloh collector for instance
      class Collector
        def initialize(ohloh_project_id, ohloh_api_key, opts = {})
          @repositories = []
          @ohloh_project_id = ohloh_project_id
          @ohloh_api_key = ohloh_api_key
          @use_data_cache = opts[:use_data_cache] || true
          @observers = opts[:observers] || []
        end

        def execute(site)
          if @use_data_cache
            modules_data_file = File.join(site.tmp_dir, 'datacache', 'components.yml')
            # TEMPORARY, but right now can't load components data if no identities data
            identities_data_file = File.join(site.tmp_dir, 'datacache', 'identities.yml')
            if File.exist? modules_data_file and File.exist? identities_data_file
              (site.components, site.modules) = YAML.load_file(modules_data_file)
              ## TEMPORARY! -> turn this into a post processor
              generate_pages(site)
              ## TEMPORARY!
              return
            else
              if File.exist? identities_data_file
                # we are going to rebuild identities
                File.unlink identities_data_file
              end
            end
          end

          more_pages = true
          page = 1
          while more_pages do
            url = "https://www.ohloh.net/p/#{@ohloh_project_id}/enlistments.xml?page=#{page}&api_key=#{@ohloh_api_key}"
            cache_key = "ohloh/enlistments-#{@ohloh_project_id}-#{page}.xml"
            # expire after 3 days
            doc = REXML::Document.new RestClient.get url, :accept => 'application/xml',
                :cache_key => cache_key, :cache_expiry_age => 86400 * 3
            doc.each_element('/response/result/enlistment/repository/url') do |e|
              git_url = e.text  
              path = File.basename(git_url.split('/').last, '.git')
              repository = OpenStruct.new({
                :path => path,
                :relative_path => '',
                :desc => nil,
                :owner => git_url.split('/').last(2).first,
                :host => URI(git_url).host,
                :type => 'git',
                :html_url => git_url.chomp('.git').sub('git://', 'https://'),
                :clone_url => git_url
              })
              @repositories << repository
            end

            offset = doc.root.elements['first_item_position'].text.to_i
            returned = doc.root.elements['items_returned'].text.to_i
            available = doc.root.elements['items_available'].text.to_i
            
            if offset + returned < available
              page += 1
            else
              more_pages = false
            end
          end

          @repositories << OpenStruct.new(
            :path => 'jboss-as',
            :desc => nil,
            :relative_path => 'arquillian/',
            :owner => 'jbossas',
            :host => 'github.com',
            :type => 'git',
            :html_url => 'https://github.com/jbossas/jboss-as',
            :clone_url => 'git://github.com/jbossas/jboss-as.git'
          )

          # still some funkiness supporting this one (page title, tags, compiled with, etc)
          @repositories << OpenStruct.new(
            :path => 'openejb',
            :desc => nil,
            :relative_path => 'openejb/arquillian/',
            :owner => 'apache',
            :host => 'github.com',
            :type => 'git',
            :html_url => 'https://github.com/apache/openejb',
            :clone_url => 'git://github.com/apache/openejb.git'
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

          @repositories.sort! {|a,b| a.path <=> b.path }

          # get the description for each github repository
          # TODO this may need review for efficiency
          @repositories.map {|r|
            r.owner if r.host == 'github.com'
          }.uniq.each {|org_name|
            org_repos_data = RestClient.get "https://api.github.com/orgs/#{org_name}/repos", :accept => 'application/json'
            @repositories.each {|r|
              #repo_data = org_repos_data.select {|c| r.owner == org_name and r.host == 'github.com' and c['name'] == r.path}
              repo_data = org_repos_data.select {|c| r.clone_url.eql? c['git_url']}
              if repo_data.size == 1
                r.desc = repo_data.first['description']
              end
            }
          }

          site.components = {}
          site.modules = {}
          site.git_author_index = {}
          @repositories.each do |r|
            ## REVIEW BEGIN
            if r.host.eql? 'github.com'
              @observers.each do |o|
                o.add_repository(r) if o.respond_to? 'add_repository'
              end
            end
            ## REVIEW END
            Visitors.defined.each do |v|
              if v.handles(r)
                v.visit(r, site)
              end
            end
          end

          # use sample commits to get the github_id for each author
          rekeyed_index = {}
          site.git_author_index.each do |email, author|
            commit_data = RestClient.get(author.sample_commit_url, :accept => 'application/json')
            github_id = commit_data['commit']['author']['login'].to_s.downcase
            if github_id.empty?
              match = site.github_mapping.find{|candidate| candidate.email.eql? author.email}
              if match
                github_id = match.github_id
                puts "Used github mapping to lookup github_id #{github_id} for #{author.name} <#{author.email}>"
              end
            end
            if !github_id.empty?
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

          # QUESTION should we do this in identities/github.rb?
          # FALLBACK ONLY BLOCK
          rekeyed_index.keys.each do |id|
            author = rekeyed_index[id]
            next unless author.github_id.nil?
            # an unmatched account will have exactly one e-mail
            unmatched_email = author.emails.first
            unmatched_name = author.name
            # attempt a name match (somewhat weak, but it will have to do)
            match = rekeyed_index.values.find{|candidate|
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

          if @use_data_cache
            FileUtils.mkdir_p File.dirname modules_data_file
            File.open(modules_data_file, 'w') do |out|
              site.components.each_pair {|k, c| c.repository.delete_field('client') }
              YAML.dump([site.components, site.modules], out)
            end
          end

          ## TEMPORARY! -> turn this into a post processor
          generate_pages(site)
          ## TEMPORARY!
        end

        def generate_pages(site)
          site.modules.each_pair {|t, modules|
            modules.each {|m|
              module_page_basepath = m.basepath + '-' + t.dasherize
              if !site.engine.nil?
                module_page = site.engine.load_site_page('modules/_module.html.haml')
              else
                module_page = OpenStruct.new
              end
              module_page.output_path = "modules/#{module_page_basepath}.html"
              # FIXME why is this needed here?
              module_page.url = "/modules/#{module_page_basepath}"
              module_page.module = m
              module_page.component = m.component
              module_page.link_name = m.name.sub(/^Arquillian /, '')
              module_page.title = m.name
              m.page = module_page
              site.pages << module_page
            }
          }
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
