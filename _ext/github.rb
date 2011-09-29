require 'rest-client'
require 'json'

module Awestruct
  module Extensions
    module GitHub

      class Org
        def initialize(org, repo_filter, resource_folder, file_extension)
          @org_name = org
          @repo_filter = repo_filter
          @resource_folder = resource_folder
          @file_extension = file_extension
        end
        
        def execute(site)
          github_tmp = tmp(site.tmp_dir)
          
          org_repo_url = "https://api.github.com/orgs/#{@org_name}/repos"
          
          # Get Organisation Repositories
          org_repo_json = getOrCache(File.join(github_tmp, "org-repos_#{@org_name}.json"), org_repo_url)

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
      
      class Repo
        def initialize(tag_filter)
          @tag_filter = tag_filter
        end

        def execute(site)
          github_tmp = tmp(site.tmp_dir)

          site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_user and page.github_repo
                tag_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/tags" 
                contributor_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/contributors"
                
                # Get Repository Tags (sort and filter)
                tag_json = getOrCache(File.join(github_tmp, "tags-#{page.github_repo}.json"), tag_url)
                tag_json = tag_json.sort{|x,y| y["name"] <=> x["name"] }
                tag_json = tag_json.select{|x| x['name'] =~ Regexp.new("#{@tag_filter}")}

                # Filter on only relevant Tags (move to page?)
                if page.github_min_tag
                  tag_json = tag_json.select{|x| x['name'] >= page.github_min_tag}
                end
  

                # Get Commit info for Tag
                tag_json.each do |tag|
                  commit_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/git/commits/#{tag['commit']['sha']}"
                  commit_json = getOrCache(File.join(github_tmp, "tag-commit-#{page.github_repo}-#{tag['name']}.json"), commit_url)
                  tag['commit'] = commit_json
                end

                # Get Repository Contributors (sort)
                contributor_json = getOrCache(File.join(github_tmp, "contributors-#{page.github_repo}.json"), contributor_url)
                contributor_json.sort{|x,y| x["login"] <=> y["login"] }

                # Get Contributors User info
                contributor_json.each do |contributor|
                  user_json = getOrCache(File.join(github_tmp, "user-#{contributor['login']}.json"), contributor['url'])
                  contributor['user'] = user_json                                    
                end

                page.github_repo_tags = tag_json
                page.github_repo_contributors = contributor_json

                #puts "Repo: #{page.github_repo} #{page.output_path}"

            end
          end
        end
      end
      
      class Release 
        def initialize(resource_folder, file_extension, from_date)
          @resource_folder = resource_folder
          @file_extension = file_extension
          @from_date = DateTime.parse from_date
        end
        
        def execute(site)
          github_tmp = tmp(site.tmp_dir)

          # Create Synthetic Release Pages if needed
          site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_user and page.github_repo and page.github_repo_tags
              page.github_repo_tags.each do |tag|
                release_date = DateTime.parse tag['commit']['committer']['date']
                
                # Only create Release Notes for commits after a configured date
                if release_date > @from_date
                  #puts "Release Repo: #{page.github_repo}-#{tag['name']}"
                  release_page = nil
                  release_page_name = "#{page.github_repo}-#{tag['name']}"
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
                  release_page.github_tag = tag

                  # Update page properties if not defined in template
                  if !release_page.title
                    release_page.title = "#{page.title} - #{tag['name']} Released"
                  end
                  if !release_page.author
                    release_page.author = tag['commit']['committer']['name']
                  end
                  if !release_page.version
                    release_page.version = tag['name']
                  end

                  if !release_page.tags
                    release_page.tags = Array[page.github_repo, page.github_user, "announcement", "release"]
                  end

                  if !release_page.date
                    release_page.date = Time.utc(release_date.year, release_date.month, release_date.day)
                  end

                end
              end
            end
          end
        end
      end
    
    end
  end
end

def getOrCache(tmp_file, json_url)
  json = ""
  if !File.exist?tmp_file
    puts json_url
    response_body = RestClient.get(json_url).body;
    json = JSON.parse response_body
    File.open(tmp_file, "w").write JSON.pretty_generate json
  else
    json = JSON.parse File.open(tmp_file, 'r').read
  end
  return json
end

def tmp(parent)
  tmp_dir = File.join(parent, "github")
  if !File.exist?tmp_dir
    Dir.mkdir(tmp_dir)
  end
  return tmp_dir
end
