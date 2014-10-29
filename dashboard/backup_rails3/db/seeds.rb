# -*- coding: utf-8 -*-
# This file should contain all the record creation needed to seed the database with its default values.
# The data can then be loaded with the rake db:seed (or created alongside the db with db:setup).
#
# Examples:
#
#   cities = City.create([{ name: 'Chicago' }, { name: 'Copenhagen' }])
#   Mayor.create(name: 'Emanuel', city: cities.first)

Dir.glob(File.dirname(__FILE__) + "/fixtures/**.yml").each { |file|
	print "reading #{file}...\n"
	fixture = File.read("#{file}")
	data = YAML.load(ERB.new(fixture).result)
	p data.keys
	data.keys.each{ |table|
		modelname = "Code::" + table.classify
		print "  #{modelname}\n"
		(modelname.constantize).create(data[table])
	}
}
p "yaml fixtures finished"

# code: [2 letter ISO639]{-[vatiant]}
Code::Language.create([
	{ code: "de", name: "German" },
	{ code: "en", name: "English" },
	{ code: "fr", name: "French" },
	{ code: "it", name: "Italian" },
	{ code: "ja", name: "Japanese" },
	{ code: "ko", name: "Korean" },
	{ code: "zh", name: "Chinese" },
	{ code: "zh-traditional", name: "Traditional Chinese" },
])

Code::Continent.create([
	{ code: "af", name: "africa" },
	{ code: "eu", name: "europe" },
	{ code: "as", name: "asia" },
	{ code: "na", name: "north_america" },
	{ code: "sa", name: "south_america" },
	{ code: "au", name: "australia" },
	{ code: "an", name: "antarctica" },
	{ code: "", name: "other" },
])

# code: [2 letter ISO 3166-1]
# These are created by fixture.
# Code::Country.create([ ])

Code::Message.create([
	{ language: "de", code: "language", content: "Sprache" },

	{ language: "en", code: "language.zh-traditional", content: "Traditional Chinese" },
	{ language: "en", code: "language", content: "Language" },
	{ language: "en", code: "continent.af", content: "Africa" },
	{ language: "en", code: "continent.eu", content: "Europe" },
	{ language: "en", code: "continent.as", content: "Asia" },
	{ language: "en", code: "continent.na", content: "North America" },
	{ language: "en", code: "continent.sa", content: "South America" },
	{ language: "en", code: "continent.au", content: "Australia" },
	{ language: "en", code: "continent.an", content: "Antarctica" },
	{ language: "en", code: "continent.", content: "Other" },

	{ language: "fr", code: "language", content: "Langage" },

	{ language: "it", code: "language", content: "Linguaggio" },

	{ language: "ja", code: "language.zh-traditional", content: "繁体中国語" },
	{ language: "ja", code: "language", content: "言語" },

	{ language: "ko", code: "language", content: "언어" },

	{ language: "zh", code: "language.zh-traditional", content: "正體中文" },
	{ language: "zh", code: "language", content: "語言" },
])

