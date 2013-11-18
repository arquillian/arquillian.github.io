module Awestruct
  class Engine
    def development?
      site.profile == 'development'
    end

    def blogging?
      site.profile == 'blogging'
    end
  end
end
