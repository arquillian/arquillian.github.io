module Awestruct::Extensions::CacheEvolver
  def evolve_cache(output_path)
    if site.evolvecache
      page = site.pages.find {|p| p.output_path.eql? output_path }
      if page
        relative_source_path = page.relative_source_path
        # TODO perhaps this should use git log info if available
        "#{output_path}?#{File.new(File.join(site.engine.config.input_dir, relative_source_path)).mtime.to_i}"
      else
        output_path
      end
    else
      output_path
    end
  end
end
