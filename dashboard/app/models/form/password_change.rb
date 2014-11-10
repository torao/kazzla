# -*- encoding: UTF-8 -*-
#
class Form::PasswordChange
  include ActiveModel::Model

  attr_accessor :old_password, :new_password1, :new_password2

end

