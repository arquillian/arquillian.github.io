require 'rest-client'
require 'json'
require 'rubygems'
require 'git'

module Awestruct
  module Extensions
    module GitHub

      class Org
        def initialize(org, repo_filter, resource_folder, file_extension, layout_expressions)
          @org_name = org
          @repo_filter = repo_filter
          @resource_folder = resource_folder
          @file_extension = file_extension
          @layout_expressions = layout_expressions
        end

        def execute(site)
          github_tmp = tmp(site.tmp_dir, 'github')

          org_repo_url = "https://api.github.com/orgs/#{@org_name}/repos"

          # Get Organisation Repositories
          org_repo_json = getOrCacheJSON(File.join(github_tmp, "repo-#{@org_name}.json"), org_repo_url)

          # Create Synthetic Pages if needed
          org_repo_json.each do |repo|
            if repo['name'] =~ Regexp.new("#{@repo_filter}")

              module_page = nil
              module_path = File.join(@resource_folder, "#{repo['name']}.#{@file_extension}")
              # Find existing page if any
              if File.exists?module_path
                module_page = site.pages.detect {|page| page.relative_source_path == "/#{@resource_folder}/#{repo['name']}.#{@file_extension}"}
              # Create a new page
              else
                module_page = site.engine.load_site_page("#{@resource_folder}/_github-module-template.#{@file_extension}")
                module_page.output_path ="#{@resource_folder}/#{repo['name']}.html"

                site.pages << module_page
              end

              # Set template layout based on name
              @layout_expressions.each do |x|
                module_page.layout = x[1] if repo['name'] =~ x[0]
              end

              ## Update page properties if not defined in template
              if !module_page.github_repo
                module_page.github_repo = repo['name'].clone
              end
              if !module_page.github_repo_owner
                module_page.github_repo_owner = @org_name
              end

              # Swap name arquillian-core with Camel Case v Arquillian Core
              if !module_page.title
                repo_name = repo['name'].clone

                repo_name.gsub!(/\-/, ' ').gsub!(/ [a-z]+/) {|segment|
                    # HACK abstract me out somehow
                    segment = ' JBoss AS' if segment == ' jbossas'
                    segment = ' GlassFish' if segment == ' glassfish'
                    segment = ' OpenEJB' if segment == ' openejb'
                    segment = ' OpenWebBeans' if segment == ' openwebbeans'
                    segment = ' OSGi' if segment == ' osgi'
                    segment = ' OpenShift' if segment == ' openshift'
                    segment = ' GAE' if segment == ' gae'
                    segment = ' WebSphere' if segment == ' was'
                    segment = ' WebLogic' if segment == ' wls'
                    segment
                  }.gsub!(/^[a-z]|\s+[a-z]/) { |a| a.upcase }
                module_page.title = repo_name
              end

              module_page.github_org_repo= repo

              #puts "Org: #{module_page.github_repo} #{module_page.output_path}"
            end
          end
        end
      end

      class Contributor

        def initialize()
          @github_tmp = nil
          @gravatar_tmp = nil
        end

        def execute(site)
          @github_tmp = tmp(site.tmp_dir, 'github')
          @gravatar_tmp = tmp(site.tmp_dir, 'gravatar')
          site.contributors ||= OpenCascade.new
          site.identities ||= OpenCascade.new

          site.pages.each do |page|
            if page.github_repo_owner and page.github_repo
              github_repo_contributors = []
              contributor_url = "https://api.github.com/repos/#{page.github_repo_owner}/#{page.github_repo}/contributors"

              # Get Repository Contributors (sort)
              contributor_json = getOrCacheJSON(File.join(@github_tmp, "contributors-#{page.github_repo}.json"), contributor_url)

              # Get Contributors User info
              contributor_json.each do |contributor|
                username = contributor['login'].downcase
                contributions = contributor['contributions']
                identity = get_identity(username, site)
                github_repo_contributors << {'contributor'=>identity, 'contributions'=>contributions}

                # if already loaded as contributor, just add contributions to running total
                if identity.contributor?.eql? true
                  identity.github.contributions += contributions
                else
                  identity.username = username
                  identity.contributor = true
                  identity.github.contributions = contributions
                  load_github_profile(username, identity, contributor['url'])
                  load_gravatar_profile(username, identity)
                  site.contributors[username.to_sym] = identity
                end
              end

              page.github_repo_contributors = github_repo_contributors
            end
          end

          # load identities that are found in identities.yml but aren't detected as contributors
          site.identities.each_pair do |username, identity|
            if identity.username?.nil?
              identity.username = username
              load_github_profile(username, identity)
              load_gravatar_profile(username, identity)
            end
          end

          # TODO looking for a more elegant way to est credentials for this call
          credentials_file = File.join(ENV['HOME'], '.github-auth')
          if File.exists? credentials_file
            credentials = File.read(credentials_file).chomp()
            speakers_url = "https://#{credentials}@api.github.com/teams/#{site.speakers_team_id}/members"
            speakers_json = getOrCacheJSON(File.join(@github_tmp, "team-speakers.json"), speakers_url)
            speakers_json.each do |speaker|
              key = speaker['login'].downcase.to_sym
              identity = site.identities[key]
              if !identity.nil? and !identity.twitter_username.nil?
                identity.speaker = true
                identity.lanyrd.url = "http://lanyrd.com/profile/#{identity.twitter_username}"
              end
            end
          end

          #puts site.identities.to_yaml
        end

        def get_identity(username, site)
          identity = site.identities[username.to_sym]
          if not identity
            identity = site.identities[username.to_sym] = OpenCascade.new
          end
          identity
        end

        def load_github_profile(username, identity, data_url = nil)
          if data_url.nil?
            data_url = "https://api.github.com/users/#{username}"
          end
          user_json = getOrCacheJSON(File.join(@github_tmp, "user-#{username}.json"), data_url)
          if user_json['blog'].nil? || user_json['blog'].empty?
            user_json['blog'] = nil
          elsif not user_json['blog'] =~ /^http:\/\//
            user_json['blog'] = 'http://' + user_json['blog']
          end
          user_json['created_at'] = Time.parse user_json['created_at']

          user_json.each_pair do |k, v|
            identity.github[k.to_sym] = v
          end

          # NOTE can't assign from one key to another, gotta use a tmp
          tmp_url = identity.github.delete(:url)
          identity.github.api_url = tmp_url
          tmp_url = identity.github.delete(:html_url)
          identity.github.url = tmp_url

          [:company, :name, :location, :bio, :email, :blog].each do |s|
            if identity[s].nil?
              identity[s] = identity.github.delete(s)
            end
          end

          identity.gravatar_hash = identity.gravatar.request_hash = identity.github.delete(:gravatar_id)
          identity.github.delete(:avatar_url)

          if not identity.gravatar_hash?.nil? || identity.gravatar_hash?.empty?
            # in templates, just add ?s=n to url to set the size of the image to nxn
            identity.avatar_url = identity.gravatar.avatar_url = "http://gravatar.com/avatar/#{identity.gravatar_hash}"
          end
        end

        def load_gravatar_profile(username, identity)
          gravatar_json = getOrCacheJSON(File.join(@gravatar_tmp, "user-#{username}.json"), "http://en.gravatar.com/#{identity.gravatar_hash}.json")
          if gravatar_json['entry']
            entry = gravatar_json['entry'].first
            identity.gravatar.id = entry['id']
            identity.gravatar.url = entry['profileUrl']
            if identity.name?.nil? || identity.name?.empty? and entry['name'].is_a?(Hash)
              identity.name = entry['name']['formatted']
            end
            if identity.location?.nil? || identity.location?.empty?
              identity.location = entry['currentLocation']
            end
            if identity.bio?.nil? || identity.bio?.empty?
              identity.bio = entry['aboutMe']
            end
            if entry['accounts']
              twitter_account = entry['accounts'].detect {|account| account['shortname'] == 'twitter'}
              if twitter_account
                identity.twitter.url = twitter_account['url'].downcase
                identity.twitter_username = identity.twitter.username = twitter_account['username'].downcase
              end 
            end
            if entry['urls']
              entry['urls'].each do |url|
                if url['value'] =~ /community\.jboss\.org\/people\//
                  identity.jboss_username = identity.jboss.username = url['value'].match(/community\.jboss\.org\/people\/(.*)/)[1]
                  identity.jboss.url = url['value']
                elsif identity.blog?.nil?
                  identity.blog = url['value'] 
                end
              end
            end
          end

          if identity.name.nil? || identity.name.empty?
            identity.name = identity.username.to_s
          end

          if identity.jboss_username? and not identity.jboss?
            identity.jboss.username = identity.jboss_username
            identity.jboss.url = 'http://community.jboss.org/people/' + identity.jboss_username
          end
        end
      end
      
      # If you touch the file _tmp/repos/.dopull, this extension will do a 'git pull' in each repo
      class Repo
        def initialize(tag_filter)
          @tag_filter = tag_filter
        end

        def execute(site)
          github_tmp = tmp(site.tmp_dir, 'github')
          repos_tmp = tmp(site.tmp_dir, 'repos')
          dopull_directive = File.join(repos_tmp, '.dopull')
          dopull = false
          if File.exist? dopull_directive
            dopull = true
            File.unlink dopull_directive
          end

          site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_repo_owner and page.github_repo
              g = nil
              github_repo_dir = File.join(repos_tmp, page.github_repo)
              if !File.exist?github_repo_dir
                repo_url = "git://github.com/#{page.github_repo_owner}/#{page.github_repo}.git"
                puts "Cloning repo from #{repo_url}..."
                g = Git.clone(repo_url, github_repo_dir)
              else
                g = Git.open(github_repo_dir)
                if dopull
                  puts "Updating repo #{page.github_repo}..."
                  g.pull('origin', 'origin/master')
                end
              end

              tags = g.tags

              # Get Repository Tags (sort and filter)
              tags = tags.sort{|x,y| y.name <=> x.name }
              tags = tags.select{|x| x.name =~ Regexp.new("#{@tag_filter}")}

              # Filter on only relevant Tags (move to page?)
              if page.github_min_tag
                tags = tags.select{|x| x.name >= page.github_min_tag}
              end

              page.git_repo = g
              page.git_tags = tags
            end
          end
        end
      end
      
      class Release 
        def initialize(resource_folder, file_extension, from_date)
          @resource_folder = resource_folder
          @file_extension = file_extension
          @from_date = Time.parse from_date
        end
        
        def execute(site)
          github_tmp = tmp(site.tmp_dir, 'github')

          # Create Synthetic Release Pages if needed
          site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_repo_owner and page.github_repo and page.git_tags and page.git_repo
              page.git_tags.each do |tag|
                commit = page.git_repo.gcommit(tag.sha)

                release_date = commit.committer_date

                # Only create Release Notes for commits after a configured date
                if release_date > @from_date
                  #puts "Release Repo: #{page.github_repo}-#{tag.name}"
                  release_page = nil
                  release_page_name = "#{page.github_repo}-#{tag.name}"
                  release_page_filename = "#{release_page_name}.#{@file_extension}"
                  release_path = "#{@resource_folder}/#{release_page_filename}"
                  # Find existing page if any
                  if File.exists?release_path
                    site.pages.each do |release_page_template|
                      #puts page.relative_source_path
                      if release_page_template.relative_source_path == "/" + release_path
                        release_page = release_page_template
                        #puts "Release Found: #{release_page_template.output_path}"

                      end
                    end
                  # Create new Page
                  else
                    #puts "Release Create new: #{page.output_path}"
                    release_page = site.engine.load_site_page("#{@resource_folder}/_github-release-template.#{@file_extension}")
                    release_page.output_path ="/#{@resource_folder}/#{release_page_name}.html"
                    release_page.relative_source_path = "/#{@resource_folder}/#{release_page_name}.html"

                    site.pages << release_page
                  end

                  release_page.github_repo = page.github_repo
                  release_page.github_repo_owner = page.github_repo_owner 
                  release_page.github_org_repo = page.github_org_repo
                  release_page.git_tag = tag
                  release_page.git_repo = page.git_repo

                  # Update page properties if not defined in template
                  release_page.title ||= "#{page.title} #{tag.name} Released"
                  release_page.author ||= commit.author.name
                  release_page.module = page.title
                  release_page.version ||= tag.name
                  release_page.layout ||= 'release'

                  page.github_repo =~ /arquillian\-(.*)/
                  module_qualifier = $1

                  release_page.tags ||= []
                  release_page.tags << "release" << module_qualifier

                  module_qualifiers = module_qualifier.split('-')
                  if module_qualifiers.length > 1
                    module_qualifiers.each do |x|
                      release_page.tags << x
                    end
                  end

                  release_page.date ||= Time.utc(release_date.year, release_date.month, release_date.day)

                end
              end
            end
          end
        end
      end
    
    end
  end
end
