class Code::Country < ActiveRecord::Base
#  attr_accessible :code, :name

  def self.countries
    @@_countries ||= Code::Country.all.sort{ |a,b| a.name <=> b.name }
    @@_countries
  end

  def self.country(code)
    countries.each{ |c|
      return c if c.code == code
    }
    nil
  end
end
