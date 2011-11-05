module Arquillian
  module Model

    class Bind
      module SiteExtras
        attr_accessor :identities
        def speakers()
          identities.reject {|login, identity| not identity.speaker? }
        end

        def identity(id)
          identity = identities[id.to_sym]
          if identity.nil?
            identity = identities.values.find {|identity| identity.name.eql? id}
            raise "No identity found for #{id}" if identity.nil?
          end
          identity
        end
      end

      def execute(site)
        @identities = site.identities
        site.speakers = site.identities.reject {|login, identity| not identity.speaker? }
        site.extend(SiteExtras)
      end
    end

  end
end
