# -*- encoding: UTF-8 -*-
#
class Form::SignIn
  include ActiveModel::Model

  attr_accessor :account, :password

  validates :account, presence: true
  validates :password, presence: true
end