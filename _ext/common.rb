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
    File.open(tmp_file, 'w').write response_body
  else
    response_body = File.read(tmp_file)
  end
  return response_body
end

def getOrCacheJSON(tmp_file, json_url)
  json = {}
  if !File.exist?tmp_file
    puts 'Grabbing ' + json_url
    response_body = RestClient.get(json_url, :accept => 'application/json') { |response, request, result, &block|
      case response.code
      when 404
          response
      else
        response.return!(request, result, &block)
      end
    }.body;
    if (response_body.match(/^(\{|\[)/))
      json = JSON.parse response_body
    end
    File.open(tmp_file, 'w').write JSON.pretty_generate json
  else
    begin
      json = JSON.parse File.read(tmp_file)
    rescue => e
      puts 'Could not parse JSON file ' + tmp_file + '; ' + e
      json = {}
    end
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
