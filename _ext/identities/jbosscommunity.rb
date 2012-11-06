module Identities
  module JBossCommunity
    class Crawler
      PROFILE_URL_TEMPLATE = 'https://community.jboss.org/people/%s'

      def crawl(identity)
        if !identity.jboss_username.nil?
          identity.jbosscommunity = OpenStruct.new({
            :username => identity.jboss_username,
            :profile_url => PROFILE_URL_TEMPLATE % identity.jboss_username
          })
        end

        if identity.jbosscommunity.nil? and !identity.urls.nil?
          identity.urls.each do |u|
            if u.title =~ /jboss community/i
              identity.jbosscommunity = OpenStruct.new({
                :username => u.value.split('/').last,
                :profile_url => u.value
              })
              identity.jboss_username = identity.jbosscommunity.username
              break
            end
          end
        end
        # TODO actually rip data from community.jboss.org
      end
    end
  end
end
