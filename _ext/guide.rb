module Awestruct
  module Extensions
    module Guide

      class Index
        def initialize(path_prefix)
          @path_prefix = path_prefix
        end

        def execute(site)
          guides = []
          
          site.pages.each do |page|
            if ( page.relative_source_path =~ /^#{@path_prefix}\/[^index]/)
              
              guide = OpenStruct.new
              guide.title = page.title
              guide.output_path = page.output_path
              guide.summary = page.guide_summary
              guide.group = page.guide_group
              guide.order = if page.guide_order then page.guide_order else 100 end
              
              page_content = Hpricot(page.content)
              chapters = []

              page_content.search('h3').each do |header_html|
                chapter = OpenStruct.new
                chapter.text = header_html.inner_html
                chapter.link_id = chapter.text.gsub(' ', '_').gsub(/[\(\)]/, '').downcase
                chapters << chapter
              end

              guide.chapters = chapters
              page.guide = guide
              guides << guide
            end
          end
          
          site.guides = guides
        end
      end

      class AddIds
      
        def transform(site, page, rendered)
          if page.guide
            page_content = Hpricot(rendered)

            page_content.search('h3').each do |header_html|
              page.guide.chapters.each do |chapter|
                if header_html.inner_html.eql? chapter.text
                  header_html.attributes['id'] = chapter.link_id
                  break
                end
              end
            end
            # FIXME bad Hpricot, what are you doing to us?
            return page_content.to_html.gsub('DOCTYPE  SYSTEM', 'DOCTYPE html')
          end
          return rendered
        end
        
      end
    end
  end
end
