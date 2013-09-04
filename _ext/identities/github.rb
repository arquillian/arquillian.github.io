module Identities
  module GitHub
    CONTRIBUTORS_URL_TEMPLATE = 'https://api.github.com/repos/%s/%s/contributors'
    TEAM_MEMBERS_URL_TEMPLATE = 'https://api.github.com/teams/%s/members'
    USER_URL_TEMPLATE = 'https://api.github.com/users/%s'
    PROFILE_URL_TEMPLATE = 'https://api.github.com/users/%s'

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
      end

      def add_repository(repository)
        @repositories << repository
      end

      def add_match_filter(match_filter)
        @match_filters << match_filter
        #File.open('/tmp/committers.yml', 'w') do |out|
        #  YAML.dump(match_filter, out)
        #end
      end

      def collect(identities)
        visited = []
        @repositories.each do |r|
          url = CONTRIBUTORS_URL_TEMPLATE % [ r.owner, r.path ]
          contributors = RestClient.get url, :accept => 'application/json'
          contributors.each do |acct|
            github_id = acct['login'].downcase
            author = nil
            @match_filters.each do |filter|
              author = filter.values.find{|candidate| candidate.github_id.eql? github_id }
              break unless author.nil?
            end
            if author.nil?
              #puts "Skipping non-Arquillian contributor #{github_id} in repository #{r.owner}/#{r.path}"
              next
            end
            identity = identities.lookup_by_github_id(github_id, true)
            github_acct_to_identity(acct, author, identity)
            visited << github_id
          end
        end

        # github doesn't keep perfect records of contributors, so handle those omitted contributors
        @match_filters.each do |filter|
          filter.values.select{|author| !author.github_id.nil? and !visited.include? author.github_id}.each do |author|
            github_id = author.github_id
            puts "Manually adding #{author.name} (#{github_id}) as a contributor"
            url = USER_URL_TEMPLATE % [ github_id ]
            user = RestClient.get url, :accept => 'application/json'
            identity = identities.lookup_by_github_id(github_id, true)
            github_acct_to_identity(user, author, identity)
          end
        end

        if !@teams.nil?
          @teams.each do |team|
            url = TEAM_MEMBERS_URL_TEMPLATE % team[:id]
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

      def github_acct_to_identity(acct, author, identity)
        # setup if first visit
        if identity.github.nil? or identity.github.contributions.nil?
          identity.github = OpenStruct.new if identity.github.nil?
          identity.github.contributions = acct.has_key?('contributions') ? acct['contributions'] : 0
          identity.github_url = acct['url'].downcase
          identity.gravatar_id = acct['gravatar_id']
          # author contains the commits we counted from analyzing the repositories
          identity.contributor = author
          identity.email = author.emails.first if identity.email.nil?
          identity.emails ||= []
          identity.emails |= [identity.email, author.emails.first]
          # alias for convenience
          identity.commits = author.commits
          # assume they want their commit name as the preferred name (unless it's an email)
          if identity.name.nil? and author.name.index('@').nil?
            if !author.name.index(' ').nil?
              # FIXME this could be made into a smarter utility function
              identity.name = author.name.split(' ').map{|n| n.capitalize}.join(' ')
              # special exception for Lincoln :)
              identity.name.sub!('Iii', 'III')
            else
              identity.name = author.name.capitalize
            end
          end
        # if been there, just add contributions according to github
        else
          identity.github.contributions += acct.has_key?('contributions') ? acct['contributions'] : 0
        end
      end

    end

    class Auth
      def initialize(auth_file = '.github-auth')
        @auth_file = auth_file
        @token = nil
      end

      def supports?(url)
        url =~ /.*github.com.*/
      end

      def invoke(request)
        token = get_token
        request.processed_headers["Authorization"] = "token #{token}" unless token.nil?
      end

      def get_token()
        if @token.nil?
          @token = false
          if !@auth_file.nil?
            if File.exist? @auth_file
              @token = File.read(@auth_file)
            elsif Pathname.new(@auth_file).relative? and File.exist? File.join(ENV['HOME'], @auth_file)
              @token = File.read(File.join(ENV['HOME'], @auth_file))
            end
          end
        end
        @token
      end
    end

    class Crawler

      def initialize(opts = {})
      end

      def enhance(identity)
        identity.extend(IdentityHelper)
      end

      def crawl(identity)
        url = identity.github_url
        if url.nil?
          if !identity.github_id.nil?
            url = PROFILE_URL_TEMPLATE % identity.github_id
          end
        end

        # can't find user, give up (no github id or explicit url)
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
        identity.emails ||= []
        identity.emails |= [identity.email].compact
        keys_to_identity = ['email', 'gravatar_id', 'blog', 'location', 'bio', 'company']
        # merge keys, overwriting duplicates
        identity.merge!(OpenStruct.new(data.select{|k, v|
          !v.to_s.strip.empty? and keys_to_identity.include? k
        }))
        if identity.email
          identity.email = identity.email.downcase
        end
        # append email if we got a new one
        identity.emails |= [identity.email]
        # fix blog urls missing http:// prefix
        if !identity.blog.nil? and identity.blog !~ /^https?:\/\//
          identity.blog = 'http://' + identity.blog
        end

        # manually credited commits
        if identity.commits and !identity.contributor
          identity.contributor = OpenStruct.new({
            :commits => identity.commits,
            :emails => [identity.email],
            :name => identity.name
          })
        end
      end
       
    end
  end
end
