require 'rest-client'
require 'json'

module Awestruct
  module Extensions
    class GitHubRepo

      def execute(site)
        github_tmp = File.join(site.tmp_dir, "github")
        if !File.exist?github_tmp 
          Dir.mkdir(github_tmp)
        end
        
        site.pages.each do |page|
            if page.is_a?(Awestruct::FrontMatterFile) and page.github_user and page.github_repo
                tag_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/tags" 
                contributor_url = "https://api.github.com/repos/#{page.github_user}/#{page.github_repo}/contributors"
                
                tag_json_tmp = File.join(github_tmp, "#{page.github_repo}-tags.json")
                #puts tag_json_tmp
                if !File.exist?tag_json_tmp
                  puts tag_url
                  tag_response = RestClient.get tag_url
                  tag_json = JSON.parse tag_response.body
                  File.open(tag_json_tmp, "w").write JSON.pretty_generate tag_json
                else
                  tag_json = JSON.parse File.open(tag_json_tmp, 'r').read
                end

                tag_json = tag_json.sort{|x,y| y["name"] <=> x["name"] }

                if page.github_min_tag
                  tag_json = tag_json.select{|x| x['name'] >= page.github_min_tag}
                end

                #puts tag_json
                
                contributor_json_tmp = File.join(github_tmp, page.github_repo + "-contributors.json")
                if !File.exist?contributor_json_tmp
                  puts contributor_url
                  contributor_response = RestClient.get contributor_url
                  contributor_json = JSON.parse(contributor_response.body)
                  File.open(contributor_json_tmp, 'w').write JSON.pretty_generate contributor_json
                else
                  contributor_json = JSON.parse File.open(contributor_json_tmp, "r").read
                end
                
                contributor_json.sort{|x,y| x["login"] <=> y["login"] }

                #puts contributor_json
                
                page.send("github_repo_tags=", tag_json)
                page.send("github_repo_contributors=", contributor_json)
                
            end
        end    
        
      end

    end
  end
end
