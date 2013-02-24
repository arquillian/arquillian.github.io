require 'restclient'
require 'time'
require 'uri'
require 'fileutils'
require 'json'

module RestClient

  class << self
    attr_reader :extensions
  end

  @extensions = []

  def self.enable(component, *args)
    @extensions.unshift [component, args]
  end

  def self.disable(component)
    @extensions.delete_if { |(existing_component, options)| component == existing_component }
  end

  class Request
    alias_method :_execute, :execute

    def execute(&block)
      if not self.headers.has_key? :redirects
        self.headers[:redirects] = 0
      else
        self.headers[:redirects] += 1
      end

      instances = []
      RestClient.extensions.each do |(component, args)|
        if (args || []).empty?
          instances << component.new(self)
        else
          instances << component.new(self, *args)
        end
      end

      response = nil
      instances.each do |instance|
        response = instance.execute(response) if instance.respond_to? 'execute'
      end

      if response.nil?
        puts 'Fetching ' + self.url.gsub(/(:\/\/)(.+:.+)(@)/, '\1xxx:xxx\3') ## don't show auth
        response = _execute &block
        instances.each do |instance|
          instance.cache_miss(response) if instance.respond_to? 'cache_miss'
        end
      end

      instances.each do |instance|
        response = instance.post_process(response) if instance.respond_to? 'post_process'
      end
      response
    end
  end

  class MockNetHTTPResponse
    attr_reader :body, :code, :header
    
    def initialize(body, code, header)
      @body = body
      @code = code
      @header = header
    end

    def to_hash
      @header.inject({}) {|out, (key, value)|
        # In Net::HTTP, header values are arrays
        out[key] = [value]
        out
      }
    end
  end

end

# TODO don't use cache dir if absolute cache file is provided
class RestGetCache
  @cache
  @cache_dir
  @cache_file
  @request

  # NOTE configuration headers are kept to be available on redirects
  def initialize(request, cache_dir = 'restcache')
    @request = request
    @redirects = @request.headers[:redirects]
    @cache_dir = cache_dir
    if request.headers.has_key? :cache
      @cache = request.headers[:cache]
    else
      @cache = true
    end
    if request.headers.has_key? :cache_expiry_age
      @cache_expiry_age = request.headers[:cache_expiry_age].to_i
    else
      @cache_expiry_age = nil
    end
    if @cache
      if request.headers.has_key? :cache_key
        @cache_file = File.join(cache_dir, request.headers[:cache_key])
      else
        uri = URI(request.url)
        path = uri.path
        path = path.gsub(/\//, '-')[1..path.length]
        query = nil
        query = uri.query.gsub(/(\?|&|=)/, '-') unless uri.query.nil?
        file_name = path
        file_name = "#{path}_#{query}" unless query.nil?

        host = uri.host
        host_basename = host.split('.')[-2, 1].first
        @cache_file = File.join(cache_dir, host_basename, file_name).downcase
        #puts "Cache File #{@cache_file}"
        if File.extname(path).empty?
          if request.headers.has_key? :accept
            @cache_file << '.' + request.headers[:accept].split('/').last
          else
            @cache_file << '.html'
          end
        end
      end
    end
  end

  def execute(response)
    if response.nil? and @cache and @request.method.eql? :get
      # read cache file if it exists and either (no expiry age is specified or the cache file is newer than now - age)
      if File.exist? @cache_file and (@cache_expiry_age.nil? or File.mtime(@cache_file) >= (Time.now - @cache_expiry_age))
        body = File.read(@cache_file)
        headers = {}
        headers = JSON.parse(File.read("#{@cache_file}.headers")) if File.exist? "#{@cache_file}.headers"
        return RestClient::Response.create(body, RestClient::MockNetHTTPResponse.new(body, 200, headers), @request.args)
      end
    end
    response
  end

  def cache_miss(response) 
    if response.code == 200 and @cache and @request.method.eql? :get and
        @redirects.eql? @request.headers[:redirects] and !response.body.empty?
      puts "Cache miss because #{@cache_file} is missing or expired"
      FileUtils.mkdir_p(File.dirname @cache_file)
      File.open(@cache_file, 'w') do |out|
        out.write response.body
      end
      #puts "Headers #{response.headers[:link].class}"
      File.open("#{@cache_file}.headers", 'w') do |out|
        out.write response.headers.inject({}) {|collector, (k,v)| collector[k] = v; collector}.to_json
      end
    end
  end
end

class RestJsonConverter
  def initialize(request, cache_dir = 'restcache')
    @parse = 'application/json'.eql? request.headers[:accept]
  end

  def post_process(response)
    if @parse
      content = response.body
      # a cached response body will already be parsed
      if content.is_a? String
        content = JSON.parse(content)
      end
      RestClient::Response.create(content, response.net_http_res, response.args)
    else
      response
    end
  end
end
