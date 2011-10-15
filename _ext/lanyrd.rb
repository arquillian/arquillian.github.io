
module Awestruct
  module Extensions
    module Lanyrd
    
      class Search
        
        def initialize(term)
          @base = 'http://lanyrd.com'
          @term = term
        end
      
        def execute(site)
          @lanyrd_tmp = tmp(site.tmp_dir, 'lanyrd')
          
          search_url = "#{@base}/search/?type=session&q=#{@term}"
          
          sessions = []
          
          pages = []
          
          page1 = Hpricot(getOrCache(File.join(@lanyrd_tmp, "search-#{@term}-1.html"), search_url))
          pages << page1
          
          extract_pages(page1, pages)
          
          pages.each do |page|
            extract_sessions(page, sessions)
          end
          
          site.sessions = sessions
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
            
            session.description = session_detail.search('div[@class*=abstract]').inner_html
            session.detail_url = session_detail_url
            
            raw.search('p[@class*=meta]').each do |meta|
              type = meta.search('strong').inner_html
              meta.search('strong').remove()
              
              if type.eql? 'Time'
                if meta.inner_text =~ /(.*[0-9]{4}) ([0-9]+:[0-9]{2}[a-z]{2})-([0-9]+:[0-9]{2}[a-z]{2})/
                  date = $1
                  start_time = $2
                  end_time = $3
                  
                  session.start_time = Time.parse "#{date} #{start_time}"
                  session.end_time = Time.parse "#{date} #{end_time}"
                end
              end
              session.raw_time =  meta.inner_text if type.eql? 'Time'
              session.event =  meta.inner_text if type.eql? 'Event'
              session.event_url =  "#{@base}#{meta.at('a').attributes['href']}" if type.eql? 'Event'
              session.speakers = []
              
              if type.eql? 'Speakers'
                meta.search('a').each do |speaker|
                  session.speakers <<  speaker.inner_text
                end
              end
              
            end
            
            sessions << session
          end
        end
        
      end
      
    end
  end
end

