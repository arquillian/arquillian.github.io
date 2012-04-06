require 'time'

module Awestruct::Extensions::Releases
  class Posts
    def initialize(path_prefix, opts = {})
      @path_prefix = path_prefix
      @for_repo_owners = opts[:for_repo_owners] || []
      @since = opts[:since] ? Time.parse(opts[:since]) : nil
    end

    def execute(site)
      site.pages.each do |page|
        # tag explicit blog entries w/ year
        if page.relative_source_path =~ /^#{@path_prefix}\/(\d{4})-\d{2}-\d{2}-/
          page.tags ||= []
          page.tags << $1.to_s
        end
      end

      site.components.each do |repo_path, component|
        # only announce our own releases
        next if not @for_repo_owners.empty? and not @for_repo_owners.include? component.owner
        component.releases.each do |release|
          next if !@since.nil? and release.date < @since
          inner_release_page = nil
          post_author = nil
          post_tags = nil
          post_title = nil
          # TODO perhaps standardize on -release suffix to make files more clear
          release_page_name = repo_path + '-' + release.version
          release_page_simple_input_path = File.join(@path_prefix, release_page_name)
          release_page_input_path = find_input_path(site.dir, release_page_simple_input_path) 
          if !release_page_input_path.nil?
            # Use existing release page if present
            # This page should always be found since awestruct should have detected it (like any other page)
            comparison_path = '/' + release_page_input_path
            inner_release_page = site.pages.find {|candidate| candidate.relative_source_path.eql? comparison_path }
            post_author = !inner_release_page.author.nil? ? inner_release_page.author : get_post_author(inner_release_page)
            post_tags = inner_release_page.tags
            post_title = inner_release_page.title
            site.pages.delete inner_release_page
          else
            # Generate release page from template if not present
            inner_release_page = site.engine.find_and_load_site_page(File.join(@path_prefix, '_release'))
            inner_release_page.output_path = File.join(@path_prefix, release_page_name + '.html')
            inner_release_page.relative_source_path = inner_release_page.output_path
          end

          # FIXME we can use site.layouts to get the layout
          layouts_dir = File.basename site.engine.config.layouts_dir
          release_page = site.engine.find_and_load_site_page(File.join(layouts_dir, 'release'))
          release_page.output_path = File.join(@path_prefix, release_page_name.tr('.', '-')) + '.html'

          release.page = release_page

          release_page.release = release
          release_page.component = component
          if post_title
            release_page.title = post_title
          else
            release_page.title ||= "#{component.name} #{release.version} Released"
          end
          release_page.author ||= !post_author.nil? ? post_author : site.identities.lookup_by_contributor(release.released_by).username
          # why do we need to do Time.utc?
          #release_page.date ||= release.date
          release_page.date = Time.utc(release.date.year, release.date.month, release.date.day)
          #release_page.layout ||= 'release'
          release_page.tags ||= []
          if post_tags
            release_page.tags += post_tags
          end
          release_page.tags << 'release' << component.type.gsub('_', '-') << component.key
          if component.type =~ /(platform|extension)/ and release.version.end_with? '.Final'
            release_page.tags << 'jbosscentral' if !release_page.tags.include? 'jbosscentral'
          end
          if site.release_notes and site.release_notes.has_key? release.key
            release_page.release_notes = site.release_notes[release.key]
          end

          # Fix date calculation performed by Posts extension
          release_page.relative_source_path =
              File.join(@path_prefix, release_page.date.strftime('%Y-%m-%d-') + release_page_name.tr('.', '-')) + '.html'

          # pre-render the content so that the entry can be wrapped in the common release text
          release_page.rendered_content = release_page.render(site.engine.create_context(release_page, inner_release_page.content))
          class << release_page
            def render(context)
              self.rendered_content
            end

            def content
              self.rendered_content
            end
          end
          site.pages << release_page
        end
      end
    end

    def find_input_path(root_dir, simple_path)
      path_glob = File.join(root_dir, simple_path + '.*')
      candidates = Dir[path_glob]
      return nil if candidates.empty?
      throw Exception.new("multiple source paths for page detected that match #{simple_path}") if candidates.size != 1
      dir_pathname = Pathname.new(root_dir)
      path_name = Pathname.new(candidates[0])
      path_name.relative_path_from(dir_pathname).to_s 
    end

    def get_post_author(page)
      rc = Git.open(page.site.dir)
      last_commit = rc.log(nil).path(page.relative_source_path[1..-1]).to_a.last
      if !last_commit.nil?
        # FIXME fragile lookup!
        page.site.identities.lookup_by_name(last_commit.author.name).username
      end
    end
  end
end
