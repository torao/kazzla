class Code::Continent < ActiveRecord::Base
  attr_accessible :code, :name

  def self.continents
    @@_continents ||= Code::Continent.all
    @@_continents
  end

  def self.continent(code)
    continents.each{ |c|
      return c if c.code == code
    }
    nil
  end

end
