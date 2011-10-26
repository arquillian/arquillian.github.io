module Awestruct
  module Extensions
    module GitHelper

      def page_authors(page)
        authors = Hash.new
        
        g = Git.open(page.site.dir)
        g.log(200).object(page.relative_source_path[1..-1]).each{ |x|
          if authors[x.author.name]
            authors[x.author.name] = authors[x.author.name] +1
          elsif
            authors[x.author.name] = 1
          end
        }
        return authors.sort{|a,b| b[1]<=>a[1]}.map{|x| x[0]}
      end
    end
  end
end
