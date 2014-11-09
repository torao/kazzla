# -*- encoding: UTF-8 -*-
#
class Form::Account
  include ActiveModel::Model

  attr_accessor :name, :contacts, :language, :timezone

  validates :name, length: { minimum: 1, maximum: 15 }
  validates :language, presence: true
  validates :timezone, presence: true

end

