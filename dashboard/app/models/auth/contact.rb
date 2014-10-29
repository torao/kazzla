class Auth::Contact < ActiveRecord::Base
#  attr_accessible :account_id, :uri, :confirmed, :confirmed_at
  belongs_to :account

	# refere as mail-address, or nil if this is not so
	def mail_address
		if uri.starts_with?("mailto:")
			uri[7..uri.length]
		else
			nil
		end
	end

end
