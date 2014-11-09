class Auth::Token < ActiveRecord::Base
  SCHEME_CONFIRM_MAIL_ADDRESS = 0
  SCHEME_RESET_PASSWORD = 1

  def self.new_token
    SecureRandom.uuid
  end
end
