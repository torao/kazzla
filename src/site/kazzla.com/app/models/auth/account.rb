require "digest/sha2"

class Auth::Account < ActiveRecord::Base
  attr_accessible :hashed_password, :last_login, :locale, :name, :salt, :timezone
  has_many :contacts, :dependent => :destroy, :foreign_key => :account_id
	has_many :password_reset_secrets, :dependent => :destroy, :foreign_key => :account_id

	attr_accessor :plain_password

	before_create :reset_salt
	before_save :encrypt_password

	def authenticate(password)
		self.hashed_password == Auth::Account.encrypt(password, self.salt)
	end

	private

	def reset_salt()
		self.salt = Auth::Account.new_salt
	end

	def encrypt_password()
		unless plain_password.nil?
			self.hashed_password = Auth::Account.encrypt(plain_password, self.salt)
		end
	end

	def self.encrypt(password, salt)
		"sha256:" + Digest::SHA256.hexdigest(password + ":" + salt)
	end

	def self.new_salt
		s = rand.to_s.tr('+', '.')
		s[0, if s.size > 32 then 32 else s.size end]
	end

end
