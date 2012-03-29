module Awestruct::Extensions
  # this could be made extensible, for now we are just adding the non-release
  # tag to entries that don't have the release tag
  class AutoTag
    def initialize(entries_name)
      @entries_name = entries_name
    end
    def execute(site)
      entries = site.send(@entries_name) || []
      entries.each do |entry|
        entry.tags ||= []
        unless entry.tags.include? 'release'
          entry.tags << 'nonrelease'
        end
      end
    end
  end
end
