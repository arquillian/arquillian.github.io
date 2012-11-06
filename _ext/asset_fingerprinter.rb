require 'digest/md5'
require 'git'

module Awestruct::Extensions::AssetFingerprinter

  # settings:
  # enable/disable
  # strategy (md5, gitlog, mtime)
  # marker (query, path)

  def fingerprint_asset(relative_source_path)
    page = site.pages.find {|p| p.relative_source_path.eql? relative_source_path }
    if page.nil?
      relative_source_path
    else
      if site.fingerprint_assets
        fp = page.fingerprint
        if fp.nil?
          #fingerprinter = Md5Fingerprinter.new
          fingerprinter = GitLogFingerprinter.new
          fp = fingerprinter.fingerprint(site, page)
          page.fingerprint = fp
        end
        "#{page.output_path}?#{fp}"
      else
        page.output_path
      end
    end
  end

  class Md5Fingerprinter
    def fingerprint(site, page)
      Digest::MD5.hexdigest(File.read(page.source_path))
      #ext = File.extname(page.output_path)
      #page.output_path = page.output_path[0..(page.output_path.length - ext.length - 1)] + '-' + fp + ext
      #page.output_path
    end
  end

  class TimestampFingerprinter
    def fingerprint(site, page)
      File.new(File.join(site.engine.config.input_dir, page.relative_source_path)).mtime.to_i
    end
  end

  class GitLogFingerprinter
    def fingerprint(site, page)
      gc = Git.open(page.site.dir)
      #fp = gc.log(1).path(page.relative_source_path[1..-1]).first.author_date.to_i
      gc.log(1).path(page.relative_source_path[1..-1]).first.sha
    end
  end
end
