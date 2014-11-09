# -*- encoding: UTF-8 -*-
#
class Form::Profile
  include ActiveModel::Model

  attr_accessor :account_id, :image, :name, :bio, :location, :url

  validates :name, length: { minimum: 1, maximum: 15 }
  validates :name, presence: true
  validates :bio, presence: true
  validates :location, presence: true
  validates :url, presence: true

end

