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

  # トップページのヘッダで <ul> による言語選択があるため <option> ではなく <li> で一覧を作成する必要がある。
  def language_options(lang)
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

  # USAGE: <%= f.select(:language, language_options_for_select(user_language, selected_value), {}, { :class => '...' }) %>
  def language_options_for_select(lang, selected)
    options_for_select(language_options(lang), selected)
  end

  # USAGE: <%= f.select(:timezone, timezone_options_for_select(user_language, selected_value), {}, { :class => '...' }) %>
  def timezone_options_for_select(lang, selected)
    @@_selectable_timezones ||= { }
    select = @@_selectable_timezones[lang]
    if select.nil?
      select = Code::Timezone.timezones.map { |tz|
        name = Code::Message.message(lang, "timezone.#{tz.code.downcase}")
        hour = format("%02d", tz.utc_offset.abs / 1000 / 60 / 60)
        min = format("%02d", tz.utc_offset.abs / 1000 / 60 % 60)
        offset = "#{tz.utc_offset < 0? "-": "+"}#{hour}:#{min}"
        [ "#{offset} #{name} (#{tz.code})", tz.code.to_s ]
      }
      @@_selectable_timezones[lang] = select
    end
    options_for_select(select, selected)
  end

  # コンタクト情報のスキーマ
  def contact_options(lang)
    [['mailto','mailto'],['tel','tel']]
  end

  def contact_select_tag(name, selected, html)
    select_tag(name, options_for_select(contact_options(user_language), { :selected => selected }), html || { })
  end

  # 指定時刻からの経過時間を人が読める形式で参照
  def elapsed_time(tm)
    span = Time.now - tm
    if span < 60
      'now'
    elsif span < 60 * 60
      "#{(span / 60).to_i} minutes"
    elsif span < 60 * 60 * 24
      "#{(span / 60 / 60).to_i} hours #{(span / 60 % 60).to_i} minutes"
    else
      "#{(span / 60 / 60 / 24).to_i} days #{(span / 60 / 60 % 24).to_i} hours"
    end
  end

end