class Auth::Contact < ActiveRecord::Base
#  attr_accessible :account_id, :uri, :confirmed_at
  belongs_to :account

	# refere as mail-address, or nil if this is not so
	def mail_address
		if uri.starts_with?("mailto:")
			uri[7..uri.length]
		else
			nil
		end
  end

  # この連絡先の確認
  def confirm(url)
    p url
    self.confirmed_at = nil
    now = Time.now
    token = Auth::Token.new({
      account_id: account_id,
      scheme: Auth::Token::SCHEME_CONFIRM_MAIL_ADDRESS,
      object: self.id,
      token: Auth::Token.new_token,
      issued_at: now,
      expired_at: now + 24 * 60 * 60
    })
    transaction {
      self.save!
      token.save!
      UserMailer.address_confirmation(self.uri, url + '?token=' + token.token).deliver
    }
  end

end
