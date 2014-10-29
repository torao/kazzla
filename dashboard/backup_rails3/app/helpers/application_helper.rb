module ApplicationHelper

	# refer user localized message without html escape
	def msg(code)
		Code::Message.message(user_language, "#{code}") || "UNDEF[msg.#{code}]"
	end

	# refer user selected language code
	def user_language
		if not @current_account.nil?
			@current_account.language
		elsif not cookies[:lang].nil?
			cookies[:lang]
		elsif not request.headers["Accept-Language"].nil?
			request.headers["Accept-Language"]
				.split(/\s*,\s*/)
				.map{ |lang|
					qvalue = 1.0
					if /(.*);\s*q\s*=\s*([01](\.[[:digit:]]{0,})?)/ =~ lang
						lang = $1
						qvalue = $2.to_f
					end
					[ qvalue, lang.strip ]
				}.sort{ |a,b| - (a[0] <=> b[0]) }.map{ |l| l[1] }.select{ |lang|
					if lang == "*"
						Code::Language::DEFAULT_CODE
					elsif /([[:alpha:]]{1,8})(-[[:alpha:]]{1,8})?/ =~ lang and Code::Language.available_language?(lang)
						$1
					else
						nil
					end 
				}.reject{ |l| l.nil? }[0] || Code::Language::DEFAULT_CODE
		else
			Code::Language::DEFAULT_CODE
		end
	end

	# <%= select_tag "id", language_options_for_select("ja") %>
	def language_options_for_select(lang)
		options_for_select(selectable_languages(lang), lang)
	end

	def selectable_languages(lang)
		@@_selectable_languages ||= { }
		select = @@_selectable_languages[lang]
		if select.nil?
			select = Code::Language.language_codes.map { |l|
				original = Code::Message.message(l, "language.#{l}")
				localized = Code::Message.message(lang, "language.#{l}")
				if original == localized
					[ original, l ]
				else
					[ "#{original} (#{localized})", l ]
				end
			}
			@@_selectable_languages[lang] = select
		end
		select
	end

	def timezone_options_for_select(lang)
		@@_selectable_timezones ||= { }
		select = @@_selectable_timezones[lang]
		if select.nil?
			select = Code::Timezone.timezones.map { |tz|
				name = Code::Message.message(lang, "timezone.#{tz.code.downcase}")
				hour = format("%02d", tz.utc_offset.abs / 1000 / 60 / 60)
				min = format("%02d", tz.utc_offset.abs / 1000 / 60 % 60)
				offset = "#{tz.utc_offset < 0? "-": "+"}#{hour}:#{min}"
				[ "#{offset} #{name} (#{tz.code})", tz.code ]
			}
			@@_selectable_timezones[lang] = select
		end
		options_for_select(select, lang)
	end

end
