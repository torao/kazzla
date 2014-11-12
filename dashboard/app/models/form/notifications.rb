# -*- encoding: UTF-8 -*-
#
class Form::Notifications
  include ActiveModel::Model

  attr_accessor :notifications, :total, :page, :items_per_page

end

