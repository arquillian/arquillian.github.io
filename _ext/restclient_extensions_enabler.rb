require_relative 'restclient_extensions'

module Awestruct::Extensions::RestClientExtensions
  class EnableGetCache
    def initialize(dir_name = 'restcache')
      @dir_name = dir_name
    end

    def execute(site)
      RestClient.enable RestGetCache, File.join(site.tmp_dir, @dir_name)
    end
  end

  class EnableJsonConverter
    def execute(site)
      RestClient.enable RestJsonConverter
    end
  end

  class EnableAuth
    def initialize(auth_modules)
      @auth_modules = auth_modules
    end
    def execute(site)
      RestClient.enable RestAuth, @auth_modules
    end
  end
end
