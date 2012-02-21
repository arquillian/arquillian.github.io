module Awestruct::Extensions::Identity
  class Bind
    module SiteExtras
      attr_accessor :identities

      def identity(id)
        identity = identities[id.to_sym]
        if identity.nil?
          identity = identities.values.find {|identity| identity.name.eql? id}
          raise "No identity found for #{id}" if identity.nil?
        end
        identity
      end

      def identity_by_twitter_username(username)
        identity = identities.values.find {|identity| identity.twitter_username.eql? username}
        raise "No identity found for twitter username #{username}" if identity.nil?
        identity
      end

      def speakers()
        identities.reject {|login, identity| not identity.speaker? }
      end

      def translators()
        identities.reject {|login, identity| not identity.translator? }
      end
    end

    def execute(site)
      @identities = site.identities
      # are these two optimizations necessary?
      site.speakers = site.identities.reject {|login, identity| not identity.speaker? }
      site.translators = site.identities.reject {|login, identity| not identity.translator? }
      site.extend(SiteExtras)
    end
  end
end
