require 'vpim/icalendar'

##
# Lanyrd is an Awestruct extension module for interacting with lanyrd.com
# to retrieve conference session listings, speakers and related info.

module Awestruct::Extensions::Lanyrd

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
  #
  # Author:: Aslak Knutsen, Dan Allen
  # TODO:: retrieve the detail url and image url for the speaker

  class Search
    
    def initialize(term)
      @base = 'http://lanyrd.com'
      @term = term
      @since = DateTime.now
    end
  
    def execute(site)
      @lanyrd_tmp = tmp(site.tmp_dir, 'lanyrd')
      
      # add &topic=#{@term} to limit sessions to those that have been tagged with the term
      search_url = "#{@base}/search/?type=session&q=#{@term}"
      
      sessions = []
      
      pages = []
      
      page1 = Hpricot(getOrCache(File.join(@lanyrd_tmp, "search-#{@term}-1.html"), search_url))
      pages << page1
      
      extract_pages(page1, pages)
      
      pages.each do |page|
        extract_sessions(page, sessions)
      end
      
      site.sessions = sessions.sort_by{|session| session.start_datetime}
    end
    
    # Find all Pages in a 'root' Page
    def extract_pages(root, pages)
      root.search('div[@class*=pagination]') do |p|
        p.search('li') do |entry|
          a = entry.at('a')
          if a
            pageinated_url = "#{@base}#{a.attributes['href']}"
        
            pageX = Hpricot(getOrCache(File.join(@lanyrd_tmp, "search-#{@term}-#{a.inner_text}.html"), pageinated_url))
            pages << pageX
          end
        end
      end
    end
    
    # Find all sessions in Page
    def extract_sessions(page, sessions)
      page.search('li[@class*=s-session]').each do |raw|
        
        event_link = raw.search('h3').at('a')
        
        session = OpenStruct.new
        session.title = event_link.inner_text
        
        session_detail_url = "#{@base}#{event_link.attributes['href']}"
        session_detail = Hpricot(getOrCache(File.join(@lanyrd_tmp, "session-#{event_link.attributes['href'].split('/').last}.html"), session_detail_url))
        
        session_meta_node = session_detail.at('div[@class=session-meta]')
        timezone_node = session_detail.at('abbr[@class=timezone]')
        if timezone_node
          session.timezone = timezone_node.inner_text
        end
        dtstart_node = session_meta_node.at('abbr[@class=dtstart]')
        if dtstart_node
          session.start_datetime = DateTime.parse [dtstart_node.attributes['title'], session.timezone].join(' ')
        end

        # skip old events and events with no date
        if session.start_datetime and session.start_datetime >= @since
          dtend_node = session_meta_node.at('abbr[@class=dtend]')
          if dtend_node
            session.end_datetime = DateTime.parse [dtend_node.attributes['title'], session.timezone].join(' ')
          end

          session.description = session_detail.search('div[@class*=abstract]').inner_html
          session.detail_url = session_detail_url
          
          raw.search('p[@class*=meta]').each do |meta|
            type = meta.search('strong').inner_html
            meta.search('strong').remove()
            session.event = meta.inner_text.strip if type.eql? 'Event'
            session.event_url = "#{@base}#{meta.at('a').attributes['href']}" if type.eql? 'Event'
            session.speaker_names = []
            
            if type.eql? 'Speakers'
              session.speaker_names = meta.inner_text.strip.split(', ')
              #meta.search('a').each do |speaker_node|
              #  session.speaker_names << speaker_node.inner_text.strip
              #end
            end
            
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

        cal = Vpim::Icalendar.create2
        site.sessions.each do |session|

          cal.add_event do |e|
            e.dtstart       Time.parse session.start_datetime.to_s
            e.dtend         Time.parse session.end_datetime.to_s
            e.summary       session.title
            e.description session.description
            e.categories    [ 'SESSION' ]
            e.url           session.detail_url
            e.set_text('LOCATION', session.event)
            e.sequence      0
            e.access_class  "PUBLIC"

            now = Time.now
            e.created       now
            e.lastmod       now

            e.organizer do |o|
              o.cn = session.event
              o.uri = session.event_url
            end

            session.speaker_names do |speaker|
              attendee = Vpim::Icalendar::Address.create(speaker)
              attendee.rsvp = true
              e.add_attendee attendee
            end
          end
        end

        input_page = File.join( File.dirname(__FILE__), 'lanyrd.export.haml' )
        page = site.engine.load_page( input_page )
        page.date = page.timestamp unless page.timestamp.nil?
        page.output_path = @output_path
        page.session_ical = cal.encode
        site.pages << page

      end
    end
  end
end
