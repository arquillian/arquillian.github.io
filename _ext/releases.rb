require 'time'
require 'ri_cal'
require 'tzinfo'
require 'versionomy'

module Awestruct::Extensions::Releases
  class Posts
    def initialize(path_prefix, opts = {})
      @path_prefix = path_prefix
      @for_repo_owners = opts[:for_repo_owners] || []
      @since = opts[:since] ? Time.parse(opts[:since]) : nil
    end

    def execute(site)
      # Create a map index of {component.name_release.version, release_page}
      # for quick cross linking lookup from other related pages
      site.release_index ||= {}
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
          post_date = nil
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
            #post_date = get_post_date(inner_release_page)
            post_date = inner_release_page.date
            post_tags = inner_release_page.tags
            post_title = inner_release_page.title
            site.pages.delete inner_release_page
          else
            # Generate release page from template if not present
            inner_release_page = site.engine.find_and_load_site_page(File.join(@path_prefix, '_release'))
            inner_release_page.output_path = File.join(@path_prefix, release_page_name + '.html')
            inner_release_page.relative_source_path = inner_release_page.output_path
          end


          inner_release_page.layout    = 'release'
          inner_release_page.release   = release
          inner_release_page.date      = release.date
          inner_release_page.component = component

          release_page = Awestruct::Page.new( site, inner_release_page )
          release_page.prepare!
          release_page.layout = 'post'
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
          if post_date
            release_page.date ||= post_date
          else
            release_page.date ||= release.date
          end
          # FIXME why do we need to do Time.utc?
          release_page.date = Time.utc(release_page.date.year, release_page.date.month, release_page.date.day)
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

          # Fix page slugging by NOT alterating the relative-source-path.
          release_page.slug = release_page_name.tr('.', '-')

          # Workaround for non inherited dynamic front matter. Manually inherit until fixed upstream #125
          inner_release_page.inherit_front_matter_from(release_page)
          site.pages << release_page

          site.release_index["#{release_page.component.name}_#{release_page.release.version}"] = release_page
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
        author_name = last_commit.author.name
        identity = page.site.identities.lookup_by_name(author_name)
        # FIXME need to be more aggressive with getting this lookup to succeed
        if identity.nil?
          puts "Cannot determine author for post " + page.relative_source_path
          author_name
        else
          identity.username
        end
      end
    end

    def get_post_date(page)
      rc = Git.open(page.site.dir)
      last_commit = rc.log(nil).path(page.relative_source_path[1..-1]).to_a.last
      if !last_commit.nil?
        last_commit.author_date
      end
    end
  end


  class FutureRelease

    FORMAT = Versionomy.default_format.modified_copy do
                 field(:release_type, :requires_previous_field => false,
                       :default_style => :short) do
                   recognize_regexp_map(:style => :long, :default_delimiter => '',
                                        :delimiter_regexp => '-|\.|\s?') do
                     map(:development, 'Dev')
                     map(:alpha, 'Alpha')
                     map(:beta, 'Beta')
                     map(:final, 'Final')
                     map(:release_candidate, 'CR')
                   end
                 end
               end


    def initialize(output_path)
      @output_path = output_path
    end

    def execute(site)
      cal = RiCal.Calendar do |cal|
        site.components.each_value do |component|
          next unless component.repository.owner.eql? 'arquillian'
          next if component.releases.empty?
          next unless component.unreleased_commits > 1

          latest_version =  Versionomy.parse(component.latest_version, FORMAT)
          latest_date = component.releases.find {|r| r.version.eql? component.latest_version}.date

          future_date = latest_date + (60 * 60 * 24 * 30) # add a month
          future_date = Time.now + (60 * 60 * 24 * 3) if future_date < Time.now
          future_version = bump_version latest_version

          cal.event do |e|
            e.dtstart = future_date
            e.dtend = future_date
            e.summary = "Release #{component.name} #{future_version}"
            e.description = YAML.dump({
              "clone_url" => component.repository.clone_url,
              "new_version" => future_version.to_s,
              "old_version" => latest_version.to_s
            })
            e.categories ['RELEASE']
            e.sequence = 0
            e.security_class = 'PUBLIC'

            e.created = latest_date
            e.organizer = "CN=Arquillian Team"

            e.alarm do |a|
              a.action = "DISPLAY"
              a.description = "Time to release #{component.name}"
              a.trigger = "-P3D"
              #a.duration = future_date - (60 * 60 * 3) # 3 days before release
            end
          end
        end
      end

      input_page = File.join( File.dirname(__FILE__), 'lanyrd.export.haml' )
      page = site.engine.load_page( input_page )
      page.date = page.timestamp unless page.timestamp.nil?
      page.output_path = @output_path
      page.session_ical = cal.export
      site.pages << page

    end

    def bump_version(v)
      return v.bump(:tiny) if v.release_type == :final
      return v.bump("#{v.release_type.to_s}_version")
    end
  end
end
