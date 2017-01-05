require 'awestruct/handlers/base_handler'

module Awestruct
  module Extensions
    # Awestruct extension creating html pages with redirect directives.
    # Configuration via _config/redirects.yml
    class RedirectCreator
      Default_Redirect_Config = "redirects"

      def initialize(*args)
        @redirect_configs = Array.new
        @redirect_configs.push(*args)
        if @redirect_configs.index(Default_Redirect_Config) == nil
          @redirect_configs.push(Default_Redirect_Config)
        end
      end

      def execute(site)
        @redirect_configs.each { |config|
          if !site[config].nil?
            site[config].each do |requested_url, target_url|
              redirect_page = Page.new(site, Handlers::RedirectCreationHandler.new( site, requested_url, target_url ))
              # make sure indexifier is ignoring redirect pages
              redirect_page.inhibit_indexifier = true
              site.pages << redirect_page
            end
          end
        }
      end
    end
  end

  module Handlers
    class RedirectCreationHandler < BaseHandler
      include Awestruct::Extensions::GoogleAnalytics

      Default_Redirect_Template = "redirects.template"
      def initialize(site, requested_url, target_url)
        super( site )
        @requested_url = requested_url
        @target_url = target_url
        @creation_time = Time.new
        @template = load_template

      end

      def simple_name
        File.basename( @requested_url, ".*" )
      end

      def output_filename
        simple_name + output_extension
      end

      def output_extension
        '.html'
      end

      def output_path
        if( File.extname( @requested_url ).empty?)
          File.join( File.dirname(@requested_url), simple_name, "index.html" )
        else
          File.join( File.dirname(@requested_url), output_filename )
        end
      end

      def content_syntax
        :text
      end

      def input_mtime(page)
        @creation_time
      end

      def rendered_content(context, with_layouts=true)
        @template
      end

      private
      def load_template
        template_file = File.join(File.dirname(__FILE__), "..", "_config", Default_Redirect_Template)
        if !File.exist?(template_file)
          abort("RedirectCreator is configured in pipeline, but redirect template (#{template_file}) does not exist")
        end
        file = File.open(template_file, "rb")
        content = file.read
        file.close
        content % {url: @target_url, google_analytics_universal: google_analytics_universal}
      end
    end
  end
end