require "digest/sha2"
require "kazzla"
include Kazzla

class Auth::Account < ActiveRecord::Base
#  attr_accessible :hashed_password, :language, :name, :salt, :timezone
  has_many :contacts, :dependent => :destroy, :foreign_key => :account_id
	has_many :password_reset_secrets, :dependent => :destroy, :foreign_key => :account_id
	has_many :nodes, :dependent => :destroy, :foreign_key => :account_id, :class_name => "Node::Node"
	belongs_to :role

  validates_uniqueness_of :name
  validates_presence_of :name

	attr_accessor :plain_password

	before_save :encrypt_password

	def authenticate(password)
		self.hashed_password == Auth::Account.encrypt(password, self.salt)
	end

	def display_name
		if name.empty?
			contacts[0].mail_address
		else
			name
		end
	end

	def can?(permission)
		not role.nil? and role.has_permission(permission)
	end

	private

	def encrypt_password()
		unless plain_password.nil?
			if self.salt.nil?
				self.salt = random_string(32)
			end
			self.hashed_password = Auth::Account.encrypt(plain_password, self.salt)
		end
	end

	def self.encrypt(password, salt)
		raise "password is nil" if password.nil?
		"sha256:" + Digest::SHA256.hexdigest(password + ":" + salt)
	end

end
