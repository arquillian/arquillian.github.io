module RedCloth::Formatters::HTML

  def before_transform(text)
    clean_html(text) if sanitize_html
    replace_emoticons(text)
  end

  # see https://github.com/jgarber/redcloth/blob/master/spec/extension_spec.rb
  def replace_emoticons(text)
    emoticon_map = {
      ';)' => 'wink',
      ':)' => 'smile',
      ':D' => 'happy',
      ':S' => 'confused'
    }
    #text.gsub!(/(\s)~(:P|:D|:O|:o|:S|:\||;\)|:'\(|:\)|:\()/) do |m|
    text.gsub!(/(\s)~(:D|:S|;\)|:\))/) do |m|
      bef,ma = $~[1..2]
      #filename = "/images/emoticons/" + (ma.unpack("c*").join('_')) + ".png"
      filename = "/images/emoticons/" + emoticon_map[ma] + ".png"
      "#{bef}<img src=\"#{filename}\" alt=\"#{ma}\" class=\"emoticon\"/>"
    end
  end
end
