class Auth::PasswordResetSecret < ActiveRecord::Base
#  attr_accessible :account_id, :issued_at, :secret
	belongs_to :account
end
