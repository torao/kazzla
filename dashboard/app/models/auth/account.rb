require "digest/sha2"
require "kazzla"
include Kazzla

class Auth::Account < ActiveRecord::Base
#  attr_accessible :hashed_password, :language, :name, :salt, :timezone
  has_many :contacts, :dependent => :destroy, :foreign_key => :account_id
	has_many :password_reset_secrets, :dependent => :destroy, :foreign_key => :account_id
	has_many :nodes, :dependent => :destroy, :foreign_key => :account_id, :class_name => "Node::Node"
  has_one :profile, :dependent => :destroy, :foreign_key => :account_id, :class_name => 'Auth::Profile'
	belongs_to :role

  validates_uniqueness_of :name
  validates_presence_of :name

	attr_accessor :plain_password

	before_save :encrypt_password
  before_create :add_profile

	def authenticate(password)
		self.hashed_password == Auth::Account.encrypt(password, self.salt)
	end

	def display_name
    if self.profile.nil? or self.profile.name.blank?
      name
    else
      self.profile.name
    end
	end

  # 全知件数を取得
  def notifications_count
    User::Notification.where(['account_id=?', id]).count
  end

  # 未読通知件数を取得
  def unread_notifications_count
    User::Notification.where(['account_id=? and read_at is null', id]).count
  end

  # ユーザへの通知を追加
  def notify_information(code, args)
    User::Notification.add(id, User::Notification::PRIORITY_INFORMATION, :dashboard, code, args)
  end

  # ユーザへイベントログを追加
  def eventlog(code, args)
    User::Notification.add(id, User::Notification::PRIORITY_EVENTLOG, :dashboard, code, args)
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

  def add_profile()
    if self.profile.nil?
      self.profile = Auth::Profile.new({ account_id: id })
    end
  end

	def self.encrypt(password, salt)
		raise "password is nil" if password.nil?
		"sha256:" + Digest::SHA256.hexdigest(password + ":" + salt)
	end

end
