require 'hpricot'

module Awestruct
  module Extensions
    module Interwiki
      def interwiki_urls(text)
        doc = Hpricot(text)
        doc.search('//a').each do |a|
          a['href'] = interwiki_url(a['href']) if not a['href'].nil?
        end
        doc
      end
    
      def interwiki_url(url)
        return url.gsub(/^profile:\/\/(.*)/, 'http://community.jboss.org/people/\1') if (url =~ /^profile:\/\//)
        return url.gsub(/^issue:\/\/(.*)/, 'https://issues.jboss.org/browse/\1') if (url =~ /^issue:\/\//)
        return url.gsub(/^space:\/\/(.*)/, 'http://community.jboss.org/en/\1') if (url =~ /^space:\/\//)
        url
      end
    end
  end
end
