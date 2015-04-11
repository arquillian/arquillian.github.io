require 'digest/md5'

module Identities
  module Gravatar

    class Crawler
      API_URL_TEMPLATE = 'http://en.gravatar.com/%s.json'
      LANYRD_PROFILE_URL_TEMPLATE = 'http://lanyrd.com/profile/%s'
      def enhance(identity)
        #identity.extend(IdentityHelper)
      end

      def crawl(identity)
        hash = identity.gravatar_id
        if hash.nil? or hash.empty?
          hash = Digest::MD5.new().update(identity.email.downcase).hexdigest 
        end
        if hash.empty? or hash.nil?
          puts "No gravatar hash could be found for #{identity.github_id}"
          return
        end
        url = API_URL_TEMPLATE % hash
        response = RestClient.get(url, :user_agent => "rest-client") do |rsp, req, res, &blk|
          if rsp.code.eql? 404
            rsp = RestClient::Response.create('{}', rsp.net_http_res, rsp.args)
            rsp.instance_variable_set(:@code, 200)
            rsp
          else
            rsp.return!(req, res, &blk)
          end
        end

        data = JSON.parse response
        if data.empty?
          return
        end

        entry = data['entry'].first
        # update with found hash. should be the same, but if we created it, we need to set identity
        identity.gravatar_id = entry['hash']
        keys_to_gravatar = {
          'id' => 'id',
          'hash' => 'hash',
          'profileUrl' => 'profile_url'
        }
        identity.gravatar = OpenStruct.new(entry.select {|k, v|
          !v.to_s.strip.empty? and keys_to_gravatar.has_key? k
        }.inject({}) {|h,(k,v)| h.store(keys_to_gravatar[k], v); h})

        keys_to_identity = {
          'preferredUsername' => 'preferred_username',
          'displayName' => 'name_cloak',
          'aboutMe' => 'bio',
          'currentLocation' => 'location'
        }
        identity.merge!(OpenStruct.new(entry.select {|k, v|
          !v.to_s.strip.empty? and keys_to_identity.has_key? k
        }.inject({}) {|h,(k,v)| h.store(keys_to_identity[k], v); h}), false)

        # TODO check if we need a merge here
        if entry.has_key? 'name' and !(entry['name'].is_a? Array) and !(name = entry['name'].to_s.strip).empty?
          if identity.names.nil?
            identity.names = OpenStruct.new(entry['name'])
          end
          if identity.name.nil?
            identity.name = identity.names.formatted
          end
        end

        if entry.has_key? 'email' and !entry['email'].to_s.strip.empty?
          email = entry['email'].downcase
          identity.email = email if identity.email.nil?
          identity.emails ||= []
          identity.emails |= [identity.email, email]
        end

        (entry['accounts'] || []).each do |a|
          astruct = OpenStruct.new(a)
          if identity.send(a['shortname']).nil?
            identity.send(a['shortname'] + '=', astruct)
          else
            identity.send(a['shortname']).merge!(astruct)
          end
        end

        # grab twitter usernames supplied by _config/identities.yml
        if identity.twitter.nil? and !identity.twitter_username.nil?
          identity.twitter = OpenStruct.new({
            :username => identity.twitter_username,
            :url => 'http://twitter.com/' + identity.twitter_username
          })
        end

        # QUESTION do we need the speaker flag check?
        if identity.speaker and !identity.twitter.nil? and identity.lanyrd.nil?
          identity.lanyrd = OpenStruct.new({
            :username => identity.twitter.username,
            :profile_url => LANYRD_PROFILE_URL_TEMPLATE % identity.twitter.username
          })
        end

        # FIXME either map url to profile_url, or change everywhere else
        (entry['urls'] || []).each do |u|
          identity.urls = [] if identity.urls.nil?
          identity.urls << OpenStruct.new(u)
          if identity.blog.to_s.empty? and u['title'] =~ /blog/i
            identity.blog = u['value']
          end
          if identity.homepage.to_s.empty? and u['title'] =~ /personal/i
            identity.homepage = u['value']
          end
        end
      end
    end
  end
end
