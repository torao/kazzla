# -*- encoding: UTF-8 -*-
#
class Form::Contact
  include ActiveModel::Model

  attr_accessor :id, :schema, :uri, :notify, :confirmed_at, :created_at

  validates :schema, inclusion: ['mailto', 'tel']
  validates :uri, format: { with: /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/i }

  def self.create(model)
    Form::Contact.new({
      id: model.id,
      schema: model.schema,
      uri: model.uri,
      notify: model.notify,
      confirmed_at: model.confirmed_at,
      created_at: model.created_at
    })
  end

  def to_model
    Auth::Contact.new({ id: id, schema: schema, uri: uri, notify: notify })
  end
end
