class Code::Language < ActiveRecord::Base
#  attr_accessible :code, :name

	DEFAULT_CODE = 'en'

	def to_iso639
		Code::Language.to_iso639(code)
	end

	def self.to_iso639(code)
		code[0, 2]
	end

	def self.languages
    @@_languages ||= Code::Language.order(:code)
		@@_languages
	end

  def self.language_codes
		@@_language_codes ||= self.languages.map{|l| l.code }
		@@_language_codes
  end

	def self.available_language?(lang)
		! self.languages.map{|l| l.code }.index(lang).nil?
	end

end
