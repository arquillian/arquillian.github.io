module Identities
  module GitHub
    CONTRIBUTOR_URL_TEMPLATE = 'https://api.github.com/repos/%s/%s/contributors'
    TEAM_MEMBERS_URL_TEMPLATE = 'https://api.github.com/teams/%s/members'

    # It appears that all github users have a gravatar_id
    AVATAR_URL_TEMPLATE = 'http://gravatar.com/avatar/%s?s=%i'
    # TODO make the default avatar configurable
    FALLBACK_AVATAR_URL_TEMPLATE = 'https://community.jboss.org/people/sbs-default-avatar/avatar/%i.png'
    module IdentityHelper
      def avatar_url(size = 48)
        if !self.gravatar_id.nil?
          AVATAR_URL_TEMPLATE % [self.gravatar_id, size]
        else
          FALLBACK_URL_TEMPLATE % size
        end
      end
    end

    class Collector

      def initialize(opts = {})
        @repositories = []
        @match_filters = []
        @teams = opts[:teams]
        @auth_file = opts[:auth_file]
        @credentials = nil
      end

      def add_repository(repository)
        @repositories << repository
      end

      def add_match_filter(match_filter)
        @match_filters << match_filter
      end

      def collect(identities)
        @repositories.each do |r|
          url = CONTRIBUTOR_URL_TEMPLATE % [ r.owner, r.path ]
          contributors = RestClient.get url, :accept => 'application/json'
          contributors.each do |c|
            github_id = c['login'].downcase
            ## BEGIN REVIEW
            author = nil
            @match_filters.each do |filter|
              author = filter[github_id]
              break unless author.nil?
            end
            # TODO it would be interesting to report who didn't get matched when it's all over
            if author.nil?
              puts "Skipping non-Arquillian contributor #{github_id} in repository #{r.owner}/#{r.path} (may be mismatched email)"
              next
            end
            ## END REVIEW
            identity = identities.lookup_by_github_id(github_id, true)
            identity.github_url = c['url'].downcase
            identity.gravatar_id = c['gravatar_id']
            # author contains the commits we counted from analyzing the repositories
            identity.contributor = author
            # alias for convenience
            identity.commits = author.commits
            # assume they want their commit name as the preferred name (unless it's an email)
            if identity.name.nil? and author.name.index('@').nil?
              if !author.name.index(' ').nil?
                identity.name = author.name.split(' ').map{|n| n.capitalize}.join(' ')
              else
                identity.name = author.name.capitalize
              end
            end
            if !identity.github.nil? and !identity.github.contributions.nil?
              identity.github.contributions += c['contributions']
            else
              identity.github = OpenStruct.new if identity.github.nil?
              identity.github.contributions = c['contributions']
            end
          end
        end

        if !@teams.nil?
          get_credentials()
          if @credentials
            @teams.each do |team|
              url = TEAM_MEMBERS_URL_TEMPLATE % team[:id]
              url = url.gsub(/^(https?:\/\/)/, '\1' + @credentials.chomp + '@')
              members = RestClient.get(url, :accept => 'application/json') 
              members.each do |m|
                github_id = m['login']
                identity = identities.lookup_by_github_id(github_id)
                # identity should not be null, mostly for testing
                if !identity.nil?
                  identity.send(team[:name] + '=', true)
                  identity.teams = [] if identity.teams.nil?
                  identity.teams << team[:name]
                end
              end
            end
          end
        end
      end

      def get_credentials()
        if @credentials.nil?
          @credentials = false
          if !@auth_file.nil?
            if File.exist? @auth_file
              @credentials = File.read(@auth_file)
            elsif Pathname.new(@auth_file).relative? and File.exist? File.join(ENV['HOME'], @auth_file)
              @credentials = File.read(File.join(ENV['HOME'], @auth_file))
            end
          end
        end
      end
    end

    class Crawler
      PROFILE_URL_TEMPLATE = 'https://api.github.com/users/%s'
      def enhance(identity)
        identity.extend(IdentityHelper)
      end

      def crawl(identity)
        url = identity.github_url
        if url.nil?
          if !identity.github_id.nil?
            url = PROFILE_URL_TEMPLATE % identity.github_id
          end
          #['github_id', 'username'].each do |k|
          #  user = identity.send(k)
          #  if !user.nil?
          #    url = PROFILE_URL_TEMPLATE % user
          #    break
          #  end
          #end
        end

        # can't find user, give up
        if url.nil?
          return
        end

        data = RestClient.get url, :accept => 'application/json'
        identity.github_id = data['login'].downcase
        identity.username = identity.github_id
        identity.github = OpenStruct.new if identity.github.nil?
        identity.github.merge!(OpenStruct.new({
          :id => data['id'],
          :created => data['created_at'],
          :username => identity.username,
          :profile_url => data['html_url'].downcase
        }))
        # only overwrite name if it's longer than the one already there
        if !data['name'].nil? and (identity.name.nil? or data['name'].length > identity.name.length)
          identity.name = data['name'].titleize
        end
        # TODO may need special handling for duplicate e-mail (make sure to grab contributor.email)
        keys_to_identity = ['email', 'gravatar_id', 'blog', 'location', 'bio', 'company']
        identity.merge!(OpenStruct.new(data.select{|k, v|
          !v.to_s.strip.empty? and keys_to_identity.include? k
        }))
        # fix blog urls missing http:// prefix
        if not identity.url.nil? and not identity.url =~ /^https?:\/\//
          identity.url += 'http://'
        end
      end
    end
  end
end
