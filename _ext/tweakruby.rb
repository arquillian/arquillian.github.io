module Kernel
  def require_relative(path)
    require File.join(File.dirname(caller.first), path.to_s)
  end
end

class Object
  def not_nil?
    not self.nil?
  end
end

class String
  def pluralize_unless_one(count, plural = nil)
    count == 1 ? self : (plural || self.pluralize)
  end

  unless method_defined?(:force_encoding)
    def force_encoding(encoding)
      self
    end

    # nokogiri tries to use Encoding.find if String responds to force_encoding
    class ::Encoding
      def self.find(encoding)
        encoding
      end
    end
  end
end

require 'ostruct'
# monkeypatch OpenStruct to remove type property warning
# and allow use of array-syntax operator and merge!
class OpenStruct
  def type
    @table[:type]
  end

  def [](key)
    @table[key.to_sym]
  end

  def []=(key, value)
    @table[key.to_sym] = value
  end

  def merge!(other, overwrite = true)
    @table.merge!(other.instance_variable_get(:@table)) { |k, o, n|
      overwrite ? n : o
    }
  end
end

module Order
  DESC = -1
  ASC = 1
end
