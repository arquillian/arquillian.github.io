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

# temporary fix for https://github.com/bobmcwhirter/awestruct/issues/68
module RedCloth
  class TextileDoc
    def initialize(string, restrictions = [:no_span_caps])
      restrictions.each { |r| method("#{r}=").call(true) }
      super(string)
    end
  end
end

module RedCloth::Formatters::HTML
  # video. vimeo 22696384 320x400
  def video(opts)
    opts[:class] = (opts[:class] ? opts[:class] + ' video' : 'video')
    source, clip_id, dim = opts[:text].split(' ').map! {|s| s.strip}
    dim_attrs = ''
    if dim
      # x is transformed by &#215; by textile
      w, h = dim.split('&#215;')
    else
      w, h = ["800", "600"]
    end
    dim_attrs = " width=\"#{w}\" height=\"#{h}\""
    html = ""
    if source == "vimeo"
      html = "<iframe#{pba(opts)}#{dim_attrs} src=\"http://player.vimeo.com/video/#{clip_id}?title=0&amp;byline=0&amp;portrait=0\" frameborder=\"0\" webkitallowfullscreen=\"webkitallowfullscreen\" mozallowfullscreen=\"mozallowfullscreen\" allowfullscreen=\"allowfullscreen\"></iframe>"
    elsif source == "youtube"
      html = "<iframe#{pba(opts)}#{dim_attrs} type=\"text/html\" src=\"http://www.youtube.com/embed/#{clip_id}\" frameborder=\"0\"></iframe>"
    elsif source == "slideshare"
      # 575x470
      html = "<iframe#{pba(opts)}#{dim_attrs} src=\"http://www.slideshare.net/slideshow/embed_code/#{clip_id}\" frameborder=\"0\" marginwidth=\"0\" marginheight=\"0\" scrolling=\"no\"></iframe>"
    end
    html
  end
end
