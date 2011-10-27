# Requires the following change to lib/awestruct/textilable.rb
# in render(context), replace begin block with:
#   rules = context.site.textile_rules ? context.site.textile_rules.map { |r| r.to_sym } : []
#   rendered = RedCloth.new( context.interpolate_string( raw_page_content ) ).to_html(*rules)
module Awestruct::Extensions
  class TextilePlus
    def initialize()
      RedCloth.send(:include, CustomRules)
    end

    def execute(site)
      site.textile_rules = [:emoticons]
    end

    # NOTE: Another approach to apply rules is to override the before_transform(text)
    # method in the HTML module (below) and explicitly invoke the rule methods
    module CustomRules
      # see https://github.com/jgarber/redcloth/blob/master/spec/extension_spec.rb
      def emoticons(text)
        emoticon_map = {
          ';)' => 'wink',
          ':)' => 'smile',
          ':D' => 'happy',
          ':S' => 'confused'
        }
        text.gsub!(/(\s)~(:D|:S|;\)|:\))/) do |m|
          bef,ma = $~[1..2]
          #filename = "/images/emoticons/" + (ma.unpack("c*").join('_')) + ".png"
          filename = "/images/emoticons/" + emoticon_map[ma] + ".png"
          "#{bef}<img src=\"#{filename}\" alt=\"#{ma}\" class=\"emoticon\"/>"
        end
      end
    end
  end
end

module RedCloth::Formatters::HTML
  # vimeo. 22696384 320x400
  def vimeo(opts)
    clip_id, dim = opts[:text].split(' ').map! {|s| s.strip}
    dim_attrs = ''
    if dim
      # x is transformed by &#215; by textile
      w, h = dim.split('&#215;')
    else
      w, h = ["800", "600"]
    end
    dim_attrs = " width=\"#{w}\" height=\"#{h}\""
    #"<object#{dim_attrs}><param name=\"allowfullscreen\" value=\"true\"/><param name=\"allowscriptaccess\" value=\"always\"/><param name=\"movie\" value=\"http://vimeo.com/moogaloop.swf?clip_id=#{clip_id}&amp;server=vimeo.com&amp;show_title=0&amp;show_byline=0&amp;show_portrait=0&amp;color=00adef&amp;fullscreen=1&amp;autoplay=0&amp;loop=0\"/><embed#{dim_attrs} allowfullscreen=\"true\" src=\"http://vimeo.com/moogaloop.swf?clip_id=#{clip_id}&amp;server=vimeo.com&amp;show_title=0&amp;show_byline=0&amp;show_portrait=0&amp;color=00adef&amp;fullscreen=1&amp;autoplay=0&amp;loop=0\" type=\"application/x-shockwave-flash\"/></object>"
    "<iframe#{pba(opts)}#{dim_attrs} src=\"http://player.vimeo.com/video/#{clip_id}?title=0&amp;byline=0&amp;portrait=0\" frameborder=\"0\" webkitallowfullscreen=\"webkitallowfullscreen\" mozallowfullscreen=\"mozallowfullscreen\" allowfullscreen=\"allowfullscreen\"></iframe>"
  end
end
