# -*- encoding : utf-8 -*-
module Awestruct::Extensions::PostsHelper
  def tag_links(tags, delimiter = ', ', style_class = nil)
    class_attr = (style_class ? ' class="' + style_class + '"' : '')
    tags.map{|tag| %Q{<a#{class_attr} href="#{tag.primary_page.url}">#{tag}</a>}}.join(delimiter)
  end
end
