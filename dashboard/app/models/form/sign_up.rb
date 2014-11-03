# -*- encoding: UTF-8 -*-
#
class Form::SignUp
  include ActiveModel::Model

  attr_accessor :name, :email, :password, :language, :timezone, :confirmed

  validates :name, length: { minimum: 1, maximum: 15 }
  validates :email, format: { with: /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/i, on: :create }
  validates :password, length: { minimum: 1 }
  validates :language, presence: true
  validates :timezone, presence: true
end