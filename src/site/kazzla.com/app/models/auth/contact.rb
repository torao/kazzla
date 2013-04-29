class Auth::Contact < ActiveRecord::Base
  attr_accessible :account_id, :uri, :confirmed, :confirmed_at
  belongs_to :account
end
