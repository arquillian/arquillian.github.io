# FIXME we need a patched atomizer to carry over our custom fields (release & component)
module Awestruct
  module Extensions
    class PatchedAtomizer

      def initialize(entries_name, output_path, opts={})
        @entries_name = entries_name
        @output_path = output_path
        @num_entries = opts[:num_entries] || 50
        @title = opts[:title] || nil
      end

      def execute(site)
        entries = site.send( @entries_name ) || []
        unless ( @num_entries == :all )
          entries = entries[0, @num_entries]
        end

        atom_pages = []

        entries.each do |entry|
          atom_pages << entry.clone
          author_id = atom_pages.last.author
          author = site.identities.lookup(author_id)
          atom_pages.last.author = author
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
