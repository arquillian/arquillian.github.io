module Awestruct
  module Extensions
    module EditPage
      def create_edit_button(page)
        page_source = page.relative_source_path[1..-1]

        # Release blog posts has a bit of a different file layout then the original
        # so if the page_source does not exist, we assume a release page and extract the metadata
        # instead of using the actual page_source
        #if !File.exists?(page_source)
        #  # in  -> blog/2012-01-13-arquillian-testrunner-spock-1-0-0-Alpha1.html
        #  # out -> blog/arquillian-testrunner-spock-1.0.0.Alpha1.textile
        #  if page_source =~ /(.*)[0-9]{4}\-[0-9]{2}\-[0-9]{2}\-(.*)([0-9].*\-[0-9].*\-[0-9].*)\.html/
        #    page_source = "#{$1}#{$2}#{$3.gsub(/\-/, '.')}.textile"
        #  end
        #end

        # not all release pages have a actual page to edit, in that case return nothing
        #if !File.exists?(page_source)
        #  puts "Not generating edit link for #{page_source}, source page not found in local repository"
        #  return ''
        #end

        %Q(<a class="btn" href="#{page.site.website_source.repo}/edit/#{page.site.website_source.branch}/#{page_source}">Edit this file</a>)
      end
    end
  end
end
