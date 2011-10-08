module Arquillian
  module Model

    class Bind
      def execute(site)
        site.arq = Site.new site
      end
    end

    class Site
      def initialize(site)
        @site = site
      end

      def user(id)
        author_info = nil
        @site.authors.each do |author| 
          if author.name.eql? id or author.jboss_profile_id.eql? id or author.twitter_id.eql? id or author.github_id.eql? id
            author_info = author
            break
          end
        end
        
        raise "no author info found for #{id}" if author_info.nil?
        
        github_info = @site.contributors[author_info.github_id]
        return User.new(author_info, github_info)
      end
    end 
    
    class User
      def initialize(id_map, github_data)
        @id_map = id_map
        @github_data = github_data
      end
      
      def fullname()
        return @id_map.name
      end
      
      def github()
        return @id_map.github_id
      end

    end
  end
end
