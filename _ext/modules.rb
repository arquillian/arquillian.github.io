# -*- encoding : utf-8 -*-

module Awestruct
  module Extensions
    module Modules

      class Page

        def execute(site)
          site.modules.each_pair {|t, modules|
            modules.each {|m|
              module_page_basepath = m.basepath + '-' + t.dasherize
              if !site.engine.nil?
                module_page = site.engine.load_site_page('modules/_module.html.haml')
              else
                module_page = OpenStruct.new
              end
              module_page.output_path = "modules/#{module_page_basepath}/index.html"
              # FIXME why is this needed here?
              module_page.url = "/modules/#{module_page_basepath}/"
              module_page.module = m
              module_page.component = m.component
              module_page.link_name = m.name.sub(/^Arquillian /, '')
              module_page.title = m.name
              m.page = module_page
              site.pages << module_page
            }
          }
        end

      end
    end
  end
end