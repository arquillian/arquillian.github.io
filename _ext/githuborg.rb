require 'rest-client'
require 'json'

=begin
Awestruct Extension that will gene
=end

module Awestruct
  module Extensions
    class GitHubOrg

      def initialize(org, repo_filter, resource_folder)
        @org_name = org
        @repo_filter = repo_filter
        @resource_folder = resource_folder
      end
      
      def execute(site)
        github_tmp = File.join(site.tmp_dir, "github")
        if !File.exist?github_tmp 
          Dir.mkdir(github_tmp)
        end

        # Fetch Remote Data
        org_repo_url = "https://api.github.com/orgs/#{@org_name}/repos"
        org_repo_json_tmp = File.join(github_tmp, @org_name + "-orgrepos.json")
        if !File.exist?org_repo_json_tmp
          puts org_repo_url
          org_repo_response = RestClient.get org_repo_url
          org_repo_json = JSON.parse org_repo_response.body
          File.open(org_repo_json_tmp, "w").write JSON.pretty_generate org_repo_json
        else
          org_repo_json = JSON.parse File.open(org_repo_json_tmp, "r").read
        end
  
        # Create Synthetic Pages
        org_repo_json.each do |repo|
          if repo["name"] =~ Regexp.new("#{@repo_filter}")
            
            module_page = "" # 'declare module_page here, else the setting of title|github_user etc does not work
            
            module_path = File.join(@resource_folder, repo["name"] + ".html.haml")
            # Find existing page if any
            if File.exists?module_path
              site.pages.each do |page|
                if page.relative_source_path =~ Regexp.new("/" + @resource_folder + "/" + repo["name"] + ".html.haml")
                  module_page = page
                end
              end
            # Create a new page
            else
              module_page = site.engine.load_site_page(@resource_folder + "/_module-template.html.haml")
              module_page.layout = "module"
              module_page.output_path = @resource_folder + "/" + repo["name"] + ".html"

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

            module_page.send("github_org_repo=", repo)

          end
          
        end
        
      end

    end
  end
end
