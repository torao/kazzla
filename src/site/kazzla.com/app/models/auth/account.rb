class Auth::Account < ActiveRecord::Base
  attr_accessible :hashed_password, :last_login, :locale, :name, :salt, :timezone
  has_many :contacts, :dependent => true, :foreign_key => :account_id
end
