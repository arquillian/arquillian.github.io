module Awestruct
  module Extensions
    module Edit

    	def create_edit_link(page)
        html = ''

        repo = page.site.website_source_repo
        branch = page.site.website_source_branch
        page_source = page.relative_source_path
        page_source = page_source[1...page_source.length] if page_source[0].eql? '/'

        # Release blog posts has a bit of a different file layout then the original
        # so if the page_source does not exist, we assume a release page and extract the metadata
        # instead of using the actual page_source
        if !File.exists?(page_source)
          # in  -> blog/2012-01-13-arquillian-testrunner-spock-1-0-0-Alpha1.html
          # out -> blog/arquillian-testrunner-spock-1.0.0.Alpha1.textile
          if page_source =~ /(.*)[0-9]{4}\-[0-9]{2}\-[0-9]{2}\-(.*)([0-9].*\-[0-9].*\-[0-9].*)\.html/
            page_source = "#{$1}#{$2}#{$3.gsub(/\-/, '.')}.textile"
          end
        end

        if !File.exists?(page_source)
          puts "Could not find page source in local repository, can not create edit link for #{page_source}"
          return '' #not all release pages have a actual page to edit, in that case return nothing
        end

        edit_url = "#{repo}/edit/#{branch}/#{page_source}"
        html += %Q(<a href="#{edit_url}">Edit</a>)
      end
    end
  end
end
