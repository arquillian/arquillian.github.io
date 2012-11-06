# Need to set the encoding on char regex constants in Ruby 1.9
if RUBY_VERSION >= "1.9"
  module Vpim
    def Vpim.encode_paramtext(value)
      case value
      when %r{\A#{Bnf::SAFECHAR.force_encoding('binary')}*\z}
        value
      else
        raise Vpim::Unencodable, "paramtext #{value.inspect}"
      end
    end

    def Vpim.encode_paramvalue(value)
      case value
      when %r{\A#{Bnf::SAFECHAR.force_encoding('binary')}*\z}
        value
      when %r{\A#{Bnf::QSAFECHAR.force_encoding('binary')}*\z}
        '"' + value + '"'
      else
        raise Vpim::Unencodable, "param-value #{value.inspect}"
      end
    end
  end
end
