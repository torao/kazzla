# -*- encoding: UTF-8 -*-
#
class Form::UserProfile
  include ActiveModel::Model

  attr_accessor :account_id, :user_name, :display_name, :bio, :location, :url

  def name
    if display_name.blank?
      user_name
    else
      display_name
    end
  end
end

