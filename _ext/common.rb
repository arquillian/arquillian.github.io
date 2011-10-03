def getOrCache(tmp_file, url)
  response_body = ""
  if !File.exist?tmp_file
    puts url
    response_body = RestClient.get(url) { |response, request, result, &block|
      case response.code
      when 404
          response
      else
        response.return!(request, result, &block)
      end
    }.body;
    File.open(tmp_file, "w").write response_body
  else
    response_body = File.open(tmp_file, 'r')
  end
  return response_body
end

def getOrCacheJSON(tmp_file, json_url)
  json = ""
  if !File.exist?tmp_file
    puts json_url
    response_body = RestClient.get(json_url) { |response, request, result, &block|
      case response.code
      when 404
          response
      else
        response.return!(request, result, &block)
      end
    }.body;
    json = JSON.parse response_body
    File.open(tmp_file, "w").write JSON.pretty_generate json
  else
    json = JSON.parse File.open(tmp_file, 'r').read
  end
  return json
end


def tmp(parent, child)
  tmp_dir = File.join(parent, child)
  if !File.exist?tmp_dir
    Dir.mkdir(tmp_dir)
  end
  return tmp_dir
end
