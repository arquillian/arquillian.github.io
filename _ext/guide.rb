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

              # make "extra chapters" a setting of the extension?
              chapter = OpenStruct.new
              chapter.text = 'Share the Knowledge'
              chapter.link_id = 'share'
              chapters << chapter

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

            guide_root = page_content.at('div[@id=guide]')

            # Wrap <div class="header"> around the h2 section
            # If you can do this more efficiently, feel free to improve it
            guide_content = guide_root.search('h2').first.parent
            indent = get_indent(get_depth(guide_content) + 2)
            in_header = true
            header_children = []
            guide_content.each_child do |child|
              if in_header
                if child.name == 'h3'
                  in_header = false
                else
                  if child.pathname == 'text()' and child.to_s.strip.length == 0
                    header_children << Hpricot::Text.new("\n" + indent)
                  else
                    header_children << child
                  end
                end
              end
            end

            guide_header = Hpricot::Elem.new('div', {:class=>'header'})
            guide_content.children[0, header_children.length] = [guide_header]
            guide_header.children = header_children
            guide_content.insert_before(Hpricot::Text.new("\n" + indent), guide_header)
            guide_content.insert_after(Hpricot::Text.new("\n" + indent), guide_header)

            guide_root.search('h3').each do |header_html|
              page.guide.chapters.each do |chapter|
                if header_html.inner_html.eql? chapter.text
                  header_html.attributes['id'] = chapter.link_id
                  break
                end
              end
            end
            return page_content.to_html.gsub(/^<!DOCTYPE [^>]*>/, '<!DOCTYPE html>')
          end
          return rendered
        end
        
        def get_depth(node)
          depth = 0
          p = node
          while p.name != 'html'
            depth += 1
            p = p.parent
          end
          depth
        end

        def get_indent(depth, ts = '  ')
          "#{ts * depth}"
        end
        
      end
    end
  end
end
