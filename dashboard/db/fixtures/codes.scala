import java.util.Locale
import java.util.Locale._
import java.util.TimeZone
import java.io._

def forSupportedLanguages(f:(Locale)=>Unit){
	List[Locale](CANADA, CANADA_FRENCH, CHINA, CHINESE, ENGLISH, FRANCE, FRENCH, GERMAN, GERMANY, ITALIAN, JAPAN, JAPANESE, KOREA, KOREAN, PRC, SIMPLIFIED_CHINESE, TAIWAN, TRADITIONAL_CHINESE, UK, US).map{ l => new Locale(l.getLanguage) }.distinct.sortBy{ _.getLanguage }.foreach{ l => f(l) }
}

val locales = Locale.getAvailableLocales.sortBy{ l => l.getLanguage }
forSupportedLanguages { language =>
	val out = new PrintWriter(new FileWriter("messages." + language.getLanguage + ".yml"))
	out.printf("message:%n")

	// language names
	locales.map{ _.getLanguage }.distinct.filter{ _ != ""}.sortBy{ _.toString }.foreach { l =>
		var lang = new Locale(l)
		out.printf("  - language: \"%s\"%n", language.getLanguage)
		out.printf("    code: \"language.%s\"%n", lang.getLanguage)
		out.printf("    content: \"%s\"%n", lang.getDisplayLanguage(language))
	}

	// country names
	locales.map{ _.getCountry }.filter{ _ != "" }.distinct.sortBy{ _.toString }.foreach{ c =>
		val country = new Locale("", c)
		out.printf("  - language: \"%s\"%n", language.getLanguage)
		out.printf("    code: \"country.%s\"%n", c.toLowerCase)
		out.printf("    content: \"%s\"%n", country.getDisplayCountry(language))
	}

	out.close
}

var out = new PrintWriter(new FileWriter("countries.yml"))
out.printf("country:%n")
locales.map{ _.getCountry }.filter{ _ != "" }.distinct.sortBy{ _.toString }.foreach{ c =>
	out.printf("  - code: \"%s\"%n", c.toLowerCase)
	out.printf("    name: \"%s\"%n", new Locale("", c).getDisplayCountry(ENGLISH))
}
out.close

val timezones = TimeZone.getAvailableIDs.filter { t => t.indexOf('/') >= 0 }.map { t => TimeZone.getTimeZone(t) }
out = new PrintWriter(new FileWriter("timezones.yml"))
out.printf("timezone:%n")
timezones.foreach{ tz =>
	out.printf("  - code: \"%s\"%n", tz.getID)
	out.printf("    name: \"%s\"%n", tz.getDisplayName(ENGLISH))
	out.printf("    utc_offset: %s%n", tz.getRawOffset.toString)
	out.printf("    daylight_saving: %s%n", tz.getDSTSavings.toString)
}
out.printf("message:%n")
forSupportedLanguages{ language =>
	timezones.foreach{ tz =>
		out.printf("  - language: \"%s\"%n", language.getLanguage)
		out.printf("    code: \"timezone.%s\"%n", tz.getID.toLowerCase)
		out.printf("    content: \"%s\"%n", tz.getDisplayName(language))
		out.printf("  - language: \"%s\"%n", language.getLanguage)
		out.printf("    code: \"timezone.%s.short\"%n", tz.getID.toLowerCase)
		out.printf("    content: \"%s\"%n", tz.getDisplayName(false, TimeZone.SHORT, language))
		out.printf("  - language: \"%s\"%n", language.getLanguage)
		out.printf("    code: \"timezone.%s.long\"%n", tz.getID.toLowerCase)
		out.printf("    content: \"%s\"%n", tz.getDisplayName(false, TimeZone.LONG, language))
	}
}
out.close


