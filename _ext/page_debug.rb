module Awestruct
  module Extensions
    module PageDebug

      # Possible to make color configurable in initializer?
      def add_debug(struct)
        
        html = ''
        html += %Q(<a style="position: absolute; top: 10px; left: 10px;" href="javascript:$('#debug').css('display', 'block')">debug</a>)
        html += %Q(<div id="debug" style="position: absolute; top: 20px; left: 20px; border: 2px solid #000; width:600px, height: 500px; display:none;background-color:#fff;">)
        html += %Q(<a href="javascript:$('#debug').css('display', 'none')">close</a>)
        html += %Q(<table>)
        
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
        
        
        html += %Q(</table>)
        html += %Q(</div>)
      end

    end
  end
end
