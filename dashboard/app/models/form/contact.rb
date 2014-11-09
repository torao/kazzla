# -*- encoding: UTF-8 -*-
#
class Form::Contact
  include ActiveModel::Model

  attr_accessor :id, :schema, :uri, :confirmed_at

  validates :schema, inclusion: ['mailto', 'tel']
  validates :uri, format: { with: /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/i }

  def self.create(model)
    Form::Contact.new({
      id: model.id,
      schema: model.schema,
      uri: model.uri,
      confirmed_at: model.confirmed_at
    })
  end

  def to_model
    Auth::Contact.new({ id: id, schema: schema, uri: uri })
  end
end
