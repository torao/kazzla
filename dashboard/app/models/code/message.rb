class Code::Message < ActiveRecord::Base
#  attr_accessible :code, :content, :country, :language

	def self.message(lang, code)
		if lang.blank?
			lang = Code::Language::DEFAULT_CODE
		end
		self.contents(lang)[code] ||
		self.contents(Code::Language.to_iso639(lang))[code] ||
		self.contents(Code::Language::DEFAULT_CODE)[code]
	end

	def self.contents(lang)
		@@_contents ||= { }
		unless @@_contents.has_key?(lang)
			@@_contents[lang] = Hash[*(Code::Message.where([ "language=?", lang]).map { |l| [ l.code, l.content] }).flatten]
		end
		@@_contents[lang]
	end

end
