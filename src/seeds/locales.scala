import java.util.Locale
import java.util.Locale._
import scala.collection.immutable._

// 使用するロケールのリスト
// val locales = Locale.getAvailableLocales
val locales = List(CANADA, CANADA_FRENCH, CHINA, CHINESE, ENGLISH, FRANCE, FRENCH, GERMAN, GERMANY, ITALIAN, JAPAN, JAPANESE, KOREA, KOREAN, PRC, SIMPLIFIED_CHINESE, TAIWAN, TRADITIONAL_CHINESE, UK, US)

// 言語コード
val languages = locales.map{ l => l.getLanguage }.filter{ l => l != null && l != "" }.foldLeft(new HashSet[String]()){ (s, l) => s + l }.toList.sortBy{ l => l }

// 地域コード
val countries = locales.map{ l => l.getCountry }.filter{ l => l != null && l != "" }.foldLeft(new HashSet[String]()){ (s, c) => s + c }.toList.sortBy{ l => l }

printf("Code::Locale.create(%n")
/*
locales.foreach { l =>
	printf("\t{ language: \"%s\", ", l.getLanguage)
	if(l.getCountry != ""){
		printf("country: \"%s\", ", l.getCountry)
	}
	printf("display_name: \"%s\" },%n", l.getDisplayName(Locale.ENGLISH))
}
*/
languages.foreach{ l =>
	val locale = new Locale(l)
	printf("\t{ language: \"%s\", display_name: \"%s\" },%n", l, locale.getDisplayLanguage(Locale.ENGLISH))
}
countries.foreach { c =>
	val locale = new Locale("", c)
	printf("\t{ country: \"%s\", display_name: \"%s\" },%n", c, locale.getDisplayCountry(Locale.ENGLISH))
}
/* Language with Country
locales.filter{ l => l.getCountry != ""}.sortBy{ l => l.getLanguage + "-" + l.getCountry }.foreach { l =>
	printf("\t{ language: \"%s\", country: \"%s\", display_name: \"%s\"},%n", l.getLanguage, l.getCountry, l.getDisplayName(Locale.ENGLISH))
}
*/
printf(")%n%n")

printf("Code::Message.create(%n")
languages.foreach { l =>
	val locale = new Locale(l)
	val fmt = "\t{ language: \"%s\", code: \"%s\", content: \"%s\" },%n"
	languages.foreach { m =>
		printf(fmt, l, "language." + m, new Locale(m).getDisplayLanguage(locale))
	}
	countries.foreach { m =>
		printf(fmt, l, "country." + m, new Locale("", m).getDisplayCountry(locale))
	}
}
printf(")%n%n")

