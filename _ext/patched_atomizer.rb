# FIXME we need a patched atomizer to carry over our custom fields (release & component)
module Awestruct
  module Extensions
    class PatchedAtomizer

      def initialize(entries_name, output_path, opts={})
        @entries_name = entries_name
        @output_path = output_path
        @num_entries = opts[:num_entries] || 50
        @title = opts[:title] || nil
        @additional_tags = opts[:additional_tags] || []
      end

      def execute(site)
        entries = site.send( @entries_name ) || []
        unless ( @num_entries == :all )
          entries = entries[0, @num_entries]
        end

        atom_pages = []

        entries.each do |entry|
          entry_clone = entry.clone
          entry_clone.author = site.identities.lookup(entry_clone.author) 
          entry_clone.additional_tags = @additional_tags
          # move date forward one day so post is not in the past
          #puts "#{entry_clone.date} #{entry_clone.date.class}"
          entry_clone.date += 1
          atom_pages << entry_clone
        end

        site.engine.set_urls(atom_pages)

        layouts_dir = File.basename site.engine.config.layouts_dir
        page = site.engine.load_page(File.join(layouts_dir, 'atom.xml.haml'))
        #page.date = page.timestamp unless page.timestamp.nil?
        page.date = atom_pages.first.date
        page.output_path = @output_path
        page.entries = atom_pages
        if @title
          page.title = @title
        elsif site.title
          page.title = site.title
        else
          page.title = site.base_url
        end
        site.pages << page
      end

    end
  end
end
