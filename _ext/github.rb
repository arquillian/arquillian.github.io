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
          org_repo_json = getOrCacheJSON(File.join(github_tmp, "org-repos_#{@org_name}.json"), org_repo_url)

          # Create Synthetic Pages if needed
          org_repo_json.each do |repo|
            if repo["name"] =~ Regexp.new("#{@repo_filter}")

              module_page = nil
              module_path = File.join(@resource_folder, "#{repo['name']}.#{@file_extension}")
              # Find existing page if any
              if File.exists?module_path
                site.pages.each do |page|
                  if page.relative_source_path =~ Regexp.new("/#{@resource_folder}/#{repo["name"]}.#{@file_extension}")
                    module_page = page
                  end
                end
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

              # Update page properties if not defined in template
              if !module_page.github_repo
                module_page.github_repo = repo["name"].clone
              end
              if !module_page.github_user
                module_page.github_user = @org_name
              end

              # Swap name arquillian-core with Camel Case v Arquillian Core
              if !module_page.title
                module_page.title = repo["name"].clone.gsub!(/\-/, ' ').gsub!(/^[a-z]|\s+[a-z]/) { |a| a.upcase }
              end

              module_page.github_org_repo= repo

              #puts "Org: #{module_page.github_repo} #{module_page.output_path}"
            end
          end
        end
      end

      class Contributor

        def execute(site)
          github_tmp = tmp(site.tmp_dir, "github")

          site.pages.each do |page|
            if page.github_user and page.github_repo
              contributor_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/contributors"

              # Get Repository Contributors (sort)
              contributor_json = getOrCacheJSON(File.join(github_tmp, "contributors-#{page.github_repo}.json"), contributor_url)
              contributor_json.sort{|x,y| x["login"] <=> y["login"] }

              # Get Contributors User info
              contributor_json.each do |contributor|
                user_json = getOrCacheJSON(File.join(github_tmp, "user-#{contributor['login']}.json"), contributor['url'])
                contributor['user'] = user_json

                site.contributors = Hash.new unless site.contributors
                site.contributors[contributor['login'].downcase] = contributor
              end

              page.github_repo_contributors = contributor_json
            end
          end
        end
      end
      
      class Repo
        def initialize(tag_filter)
          @tag_filter = tag_filter
        end

        def execute(site)
          github_tmp = tmp(site.tmp_dir, 'github')

          site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_user and page.github_repo
              # Clone the repo
              github_repo_tmp = File.join(github_tmp, 'repo')
              if !File.exist?github_repo_tmp
                Dir.mkdir(github_repo_tmp)
              end

              g = nil
              github_repo_dir = File.join(github_repo_tmp, page.github_repo)
              if !File.exist?github_repo_dir
                repo_url = "https://github.com/#{page.github_user}/#{page.github_repo}.git"
                puts "#{repo_url}"
                g = Git.clone(repo_url, github_repo_dir)
              else
                g = Git.open(github_repo_dir)
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
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_user and page.github_repo and page.git_tags and page.git_repo
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
                  release_page.github_user = page.github_user 
                  release_page.github_org_repo = page.github_org_repo
                  release_page.git_tag = tag
                  release_page.git_repo = page.git_repo

                  # Update page properties if not defined in template
                  release_page.title = "#{page.title} #{tag.name} Released" unless release_page.title
                  release_page.author = commit.author.name unless release_page.author
                  release_page.version = tag.name unless release_page.version
                  release_page.layout = 'release' unless release_page.layout

                  page.github_repo =~ /arquillian\-(.*)/
                  module_qualifier = $1

                  release_page.tags = Array[] unless release_page.tags
                  release_page.tags << "release" << module_qualifier

                  module_qualifiers = module_qualifier.split('-')
                  if module_qualifiers.length > 1
                    module_qualifiers.each do |x|
                      release_page.tags << x
                    end
                  end

                  release_page.date = Time.utc(release_date.year, release_date.month, release_date.day) unless release_page.date

                end
              end
            end
          end
        end
      end
    
    end
  end
end
