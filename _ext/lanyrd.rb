require 'ri_cal'
require 'tzinfo'
require 'nokogiri'
require 'rest-client'
require_relative 'common.rb'
##
# Lanyrd is an Awestruct extension module for interacting with lanyrd.com
# to retrieve conference session listings, speakers and related info.

module Awestruct
  module Extensions
    module Lanyrd

  ##
  # Awestruct::Extensions::Lanyrd::Search retrieves sessions from
  # lanyrd.com that match the given search term.
  #
  # This class is loaded as an extension in the Awestruct pipeline. The
  # constructor accepts a search term that is used to search for sessions.
  #
  #   extension Awestruct::Extensions::Lanyrd::Search.new('arquillian')
  #
  # This extension performs the following work:
  #
  # * search for the specified term using an HTTP request
  # * parse the HTML result for pagination and fetch the paged results
  # * parse all pages for sessions and fetch the session details
  # * drop any sessions prior to the current date or with a missing date
  # * store an array of OpenStruct objects representing sessions in site.sessions
  #
  # Each OpenStruct session object contains the following properties:
  #
  # * title (title of session, type: String)
  # * description (abstract from session page, type: String (HTML))
  # * detail_url (session detail page at lanyrd.com, type: URL (absolute))
  # * start_datetime (start time of session, type: DateTime)
  # * end_datetime (end time of session, type: DateTime)
  # * timezone (canonical timezone of session, type: String)
  # * event (conference name, type: String)
  # * event_url (conference URL, type: URL (absolute))
  # * speaker_names (full name of all speakers, type: Array[String])
  # * speakers (username and name of all speakers, type: Hash[username, name])
  #
  # Author:: Aslak Knutsen, Dan Allen
  # TODO:: retrieve the detail url and image url for the speaker, don't download detail pages of old sessions

  class Search
    
    def initialize(term)
      @base = 'http://lanyrd.com'
      @term = term
      @since = Time.now
    end
  
    def execute(site)
      @lanyrd_tmp = tmp(site.tmp_dir, 'lanyrd')
      
      # add &topic=#{@term} to limit sessions to those that have been tagged with the term
      # context=future only works for conferences atm...waiting for session search support
      search_url = "#{@base}/search/?type=session&q=#{@term}"
      
      sessions = []
      
      pages = []
      
      page1 = Nokogiri::HTML(getOrCache(File.join(@lanyrd_tmp, "search-#{@term}-1.html"), search_url))
      pages << page1
      
      extract_pages(page1, pages, search_url)
      pages.each do |page|
        extract_sessions(page, sessions)
      end
      
      site.sessions = sessions.sort_by{|session| session.start_datetime}
    end
    
    # Find all Pages in a 'root' Page
    def extract_pages(root, pages, search_url)
      # lanyrd pageination show 1, 2, 3... 5
      # Get the last index and loop
      last_page_index = 1
      root.css('div[@class*=pagination]').each do |p|
        last_page_index = Integer(p.search('li').last.at('a').inner_text) +1
      end
      for index in 2...last_page_index
        pageinated_url = "#{search_url}&page=#{index}"
        pageX = Nokogiri::HTML(getOrCache(File.join(@lanyrd_tmp, "search-#{@term}-#{index}.html"), pageinated_url))
        pages << pageX
      end
    end
    
    # Find all sessions in Page
    def extract_sessions(page, sessions)
      page.css('li[@class*=s-session]').each do |raw|
        
        event_link = raw.css('h3').at('a')
        
        session = OpenStruct.new
        session.title = event_link.inner_text
        
        session_detail_url = "#{@base}#{event_link.attribute('href')}"
        session.slug = event_link.attribute('href').to_s.split('/').last
        session_detail = Nokogiri::HTML(getOrCache(File.join(@lanyrd_tmp, "session-#{session.slug}.html"), session_detail_url))
        session.updated = File.mtime(File.join(@lanyrd_tmp, "session-#{session.slug}.html"))
        
        session_meta_node = session_detail.css('div[@class=session-meta]').first
        timezone_node = session_detail.css('abbr[@class=timezone]').first
        if timezone_node
          session.timezone = timezone_node.attribute('title').to_s
        end

        tz = nil
        tz = TZInfo::Timezone.get(session.timezone.to_s) unless session.timezone.nil?

        dtstart_node = session_meta_node.css('abbr[@class=dtstart]').first
        if dtstart_node
          session.start_datetime = Time.parse dtstart_node.attribute('title')
          session.start_datetime = tz.local_to_utc(session.start_datetime) unless tz.nil?
        end

        # skip old events and events with no date
        if session.start_datetime and session.start_datetime >= @since
          dtend_node = session_meta_node.at('abbr[@class=dtend]')
          if dtend_node
            session.end_datetime = Time.parse dtend_node.attribute('title')
            session.end_datetime = tz.local_to_utc(session.end_datetime) unless tz.nil?
          end

          session.description = session_detail.css('div[@class*=abstract]').first.inner_html
          session.detail_url = session_detail_url

          session.event_location = session_detail.css('p[@class=location]').first.inner_text.split(/\//).map{|l| l.strip}.reverse.join(', ')
          
          session.speaker_names = []
          session.speakers = []

          raw.css('p[@class*=meta]').each do |meta|
            type = meta.css('strong').inner_html
            meta.css('strong').remove()
            session.event = meta.inner_text.strip if type.eql? 'Event'
            session.event_url = "#{meta.css('a').first.attribute('href')}" if type.eql? 'Event'
            
            if type.eql? 'Speakers'
              session.speaker_names = meta.inner_text.strip.split(', ')
              meta.css('a').each do |speaker_node|
                name = speaker_node.inner_text.strip
                username = speaker_node.attribute('href').match(/\/profile\/([^\/]*)/)[1]
                # QUESTION add identities for speakers if not exist?
                session.speakers << {'name' => name, 'username' => username }
                end
            end
          end

          # updated Speaker lanyrd layout 2013-06-26
          session_detail.css('ul[@class*=user-list]').each do |users|
            name = nil
            users.css('span[@class*=user-name]').each do |user|
              name = user.text()
              session.speaker_names << name
            end
            username = nil
            users.css('li a').each do |a|
              username = a.attribute('href').to_s.match(/\/profile\/([^\/]*)/)[1]
            end
            session.speakers << {'name' => name, 'username' => username }
          end
          
          sessions << session
        end
      end
    end
  end

  ##
  # Awestruct::Extensions::Lanyrd::Export exports sessions retrived by the Search extension
  # as a ical stream.
  #
  # This class is loaded as an extension in the Awestruct pipeline. The
  # constructor accepts a output_path to where the ical stream should be exported.
  #
  #   extension Awestruct::Extensions::Lanyrd::Export.new('/invation/events/ical.ics')
  #
  # This extension performs the following work:
  #
  # * read all site.sessions
  # * write out a ical using the vpim library
  #
  # Author:: Aslak Knutsen
  class Export

    def initialize(output_path)
      @output_path = output_path
    end

    def execute(site)
      if site.sessions

        cal = RiCal.Calendar do |cal|
          site.sessions.each do |session|

            cal.event do |e|
              e.dtstart = session.start_datetime
              e.dtend = session.end_datetime
              e.summary = session.title
              e.description = session.description
              e.categories ['SESSION']
              e.url = session.detail_url
              e.location = session.event
              e.sequence = 0
              e.security_class = 'PUBLIC'

              e.created = session.updated
              e.last_modified = session.updated

              e.organizer = "CN=#{session.event}:#{session.event_url}"

              session.speakers.each do |speaker|
                e.add_attendee speaker['name']
              end
            end
          end
        end

        input_page = File.join( File.dirname(__FILE__), 'lanyrd.export.haml' )
        page = site.engine.load_page( input_page )
        page.date = page.timestamp unless page.timestamp.nil?
        page.output_path = @output_path
        page.session_ical = cal.export
        site.pages << page

      end
    end
  end

    end
  end
end
