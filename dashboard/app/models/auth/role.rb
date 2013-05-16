class Auth::Role < ActiveRecord::Base
  attr_accessible :name, :permissions
	belongs_to :user

	def has_permission?(permission)
		@_permissions ||= self.permissions.split(/\s*,\s/).map{|p| p.downcase }
		! @_permissions.index(permission.to_s.downcase).nil?
	end

end
