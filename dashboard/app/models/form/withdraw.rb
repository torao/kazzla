# -*- encoding: UTF-8 -*-
#
class Form::Withdraw
  include ActiveModel::Model

  attr_accessor :confirmed

  validates_acceptance_of :confirmed, message: 'Please check if you really want to withdraw.'

end

