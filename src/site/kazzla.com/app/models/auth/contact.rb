class Auth::Contact < ActiveRecord::Base
  attr_accessible :account_id, :uri
  belongs_to :account
end
