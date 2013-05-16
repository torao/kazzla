class Code::Timezone < ActiveRecord::Base
  attr_accessible :code, :daylight_saving, :name, :utc_offset

	def self.timezones
		@@_timezones ||= Code::Timezone.find(:all, :order => :utc_offset)
		@@_timezones
	end

end
