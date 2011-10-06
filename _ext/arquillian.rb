require 'rexml/document'

module Awestruct
  module Extensions
    module Arquillian

      class TagInfo
        
        def execute(site)
          site.pages.each do |page| 
            if page.git_repo and page.git_tags
              repo = page.git_repo

              tags_info = Hash.new

              page.git_tags.each do |tag|
                tag_info = OpenStruct.new
                tag_info.containers = Array[]
                
                tag_tree = repo.gtree tag.sha
                tag_tree.children.each do |entry|
                  if entry[0].eql? "pom.xml"
                    pom_content = REXML::Document.new(repo.object(entry[1]).contents)
                    pom_content.elements.each('project/properties/version.arquillian_core') do |core_version|
                      tag_info.arquillian_core_version = core_version.text
                    end
                    pom_content.elements.each('project/modules/module') do |x| 
                      tag_info.containers << x.text if x.text =~ /.*\-(managed|remote|embedded).*/
                    end
                  end
                end
                tags_info[tag.name] = tag_info                  
              end
              page.arq_tags_info = tags_info
            end
          end
        end
      end
      
      class JiraVersionPrefix
        
        def execute(site)
          site.pages.each do |page|
            if page.github_repo
              version_prefix = page.github_repo
              version_prefix = version_prefix.split('-').last
              if version_prefix.eql? 'core'
                version_prefix = '' 
              else
                version_prefix = "#{version_prefix}_"
              end
            end
            page.jira_version_prefix = version_prefix
          end
        end
      end

    end
  end
end
