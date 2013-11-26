require 'pathname'

module Identities
  module Confluence
    class Crawler
      SESSION_PATH = '/rest/prototype/1/session'
      SEARCH_PATH = '/rest/prototype/1/search/user'
      SEARCH_PARAM = 'query'

      # Public: Initialize a Crawler
      #
      # base_url     - The String of the base confluence url, no trailing slash
      # opts[:assign_to] - The String OpenStruct keys to send the data
      # opts[:identity_search_keys] - The String OpenStruct keys to use to locate the search query
      # opts[:assign_username_to] - The String OpenStruct key to which to assign the username
      # opts[:auth_file] - The File containing the authentication credentials for secure requests
      def initialize(base_url, opts = {})
        @base_url = base_url
        @identity_search_keys = opts[:identity_search_keys] || ['name']
        @assign_to = opts[:assign_to] || 'confluence'
        @assign_username_to = opts[:assign_username_to]
        @auth_file = opts[:auth_file]
        @session_cookie = nil
      end

      def crawl(identity)
        search = nil
        queries = []
        data = nil
        return unless identity.send(@assign_username_to).nil?
        @identity_search_keys.each do |k|
          search = identity.send(k)
          next if search.nil?
          # lowercase and remove middle names (shouldn't affect usernames)
          search = search.downcase.gsub(/^([^ ]+ ?).*?([^ ]+)$/, '\1\2')
          # don't search on same string twice
          next if queries.include? search
          queries << search
          cleansed_query = search.gsub(' ', '-')
          url = File.join(@base_url, SEARCH_PATH) + '?query=' + URI.encode(search)
          data = RestClient.get(url, :cache_key => "confluence/query-#{cleansed_query}.json", :accept => 'application/json')
          if data['totalSize'].to_i == 0
            #puts "No results, advancing confluence user crawler to next query string for #{search}"
            data = nil
          #elsif data['totalSize'] > 1
          #  puts "Too many results, skipping confluence user crawler for #{search}."
          #  data = nil
          else
            break
          end
        end

        if data.nil?
          if queries.empty?
            puts "No property found to search, skipping confluence user crawler for #{identity.name}"
          else
            puts "No results, skipping confluence user crawler for #{queries.join ' / '}"
          end
          return
        end

        user = data['result'].first 
        username = user['username']
        url = user['link'].select{|l| l['href'].end_with? "~#{username}" }.first['href'] 
        identity.send(@assign_to + '=', OpenStruct.new({:username => username, :profile_url => url}))
        if !@assign_username_to.nil?
          identity.send(@assign_username_to + '=', username)
        end

        get_session_cookie()
        if @session_cookie
          profile = RestClient.get(url, :cache_key => "confluence/user-#{username}.html", :cookie => @session_cookie)
          if profile.match(/ id="email".*?>(.+?)</)
            replace = {' dot ' => '.', ' at ' => '@'}
            email = $1.gsub(/ (dot|at) /) {|m| replace[m]}
            identity.email = email if identity.email.nil?
            identity.emails ||= []
            identity.emails |= [identity.email, email]
          end
        end

      end

      def get_session_cookie()
        if @session_cookie.nil?
          @session_cookie = false
          credentials = nil
          if !@auth_file.nil?
            if File.exist? @auth_file
              credentials = File.read(@auth_file)
            elsif Pathname.new(@auth_file).relative? and File.exist? File.join(ENV['HOME'], @auth_file)
              credentials = File.read(File.join(ENV['HOME'], @auth_file))
            end
          end
          if !credentials.nil?
            url = @base_url.gsub(/^(https?:\/\/)/, '\1' + credentials.chomp + '@') + SESSION_PATH
            response = RestClient.head(url)
            @session_cookie = response.headers[:set_cookie].first.split(';').first
          end
        end
      end
    end
  end
end
