require 'rest-client'
require 'json'

module Awestruct
  module Extensions
    module Jira
  
      class ReleaseNotes
        
        def initialize(jira_key, jira_project_id)
          @jira_key = jira_key
          @jira_project_id = jira_project_id
        end
        
        def execute(site)
          jira_tmp = tmp(site.tmp_dir, 'jira')
          jira_url = "https://issues.jboss.org/rest/api/latest/project/#{@jira_key}"

          jira_json = getOrCacheJSON(File.join(jira_tmp, "jira-#{@jira_key}.json"), jira_url)
          jira_json['versions'] = jira_json['versions'].select{|x| x['released']}
          
          jira_json['versions'].each do |version|
            version_number = version['self'].split('/').last
            
            jira_release_url = "https://issues.jboss.org/secure/ReleaseNote.jspa?projectId=#{@jira_project_id}&version=#{version_number}"
            jira_release_response = getOrCache(File.join(jira_tmp, "jira-release-#{@jira_key}-#{version_number}.html"), jira_release_url)
  
            # Remove all other html besides the pure <li> elements with issues info
            release_notes = Hpricot(jira_release_response).at('#editcopy').following_siblings
            release_notes = release_notes.search('li')
            
            version['releaseNotes'] = "<ul>#{Hpricot::uxs(release_notes.to_html)}</ul>"
          end
          
          site.jira_release = jira_json
        end
          
      end

    end
  end
end
