module Awestruct
  module Extensions
    module PageDebug

      def add_debug(site, page)
        html = ''
        if (site.show_debug)
          html += %Q(<a style="position: absolute; top: 10px; left: 10px;" href="#" onclick="$('#debug_site').toggle()">site</a>)
          html += create(site, "debug_site")
          html += %Q(<a style="position: absolute; top: 30px; left: 10px;" href="#" onclick="$('#debug_page').toggle()">page</a>)
          html += create(page, "debug_page")
        end
        return html
      end

      def create(struct, id)
        html = ''
        html += %Q(<div id="#{id}" style="position: absolute; top: 20px; left: 20px; border: 2px solid #000; width:600px, height: 500px; display:none;background-color:#fff;">)
        html += %Q(<a href="#" onclick="$('##{id}').toggle()">close</a>)
        html += %Q(<table class="data">)

        html += introspect(struct)

        html += %Q(</table>)
        html += %Q(</div>)
        return html
      end

      def introspect(struct)
        html = ''
        table = struct.instance_variable_get("@table")
        table.sort{|a,b| a[0].to_s<=>b[0].to_s}.each do |arr| 
          key = arr[0]
          value = arr[1]
          #puts "#{key} -> #{value.class}"

          output_value = nil

          if value.is_a?(NilClass)
            output_value = 'nil'
          elsif value.is_a?(Awestruct::FrontMatterFile)
            output_value = value.class
          elsif value.is_a?(Hash)
            output_value = "<pre>#{JSON.pretty_generate value}</pre>"
          elsif value.is_a?(Array)
            if value[0].is_a?(Hash)
              output_value = "<pre>#{JSON.pretty_generate value}</pre>"
            elsif value[0].is_a?(Awestruct::FrontMatterFile)
              output_value = value.collect {|x| x.class}.join(', ')
            else
              output_value = value.join(', ')
            end
          else
            output_value = value;
          end

          html += %Q(<tr><th style="background-color:#eee;">#{key}</th><td style="background-color:#fff;">#{output_value}&nbsp;</td></tr>)
        end
        return html
      end
    end
  end
end
