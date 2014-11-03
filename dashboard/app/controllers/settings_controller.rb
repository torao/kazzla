class SettingsController < ApplicationController
  before_filter :signin_required

  def account
    if request.get?
      @account = Form::Account.new
      @account.name = @current_account.name
      @account.contacts = @current_account.contacts
      @account.language = @current_account.language
      @account.timezone = @current_account.timezone
    end
    if request.put?
      a = params[:auth_account]
      account = @current_account
      account.language = a[:language]
      account.timezone = a[:timezone]
      account.save!
    end
  end

  def security
  end

  def password
    if request.post?
      pass = params[:current_password]
      pass1 = params[:new_password1]
      pass2 = params[:new_password2]
      if not @current_account.authenticate(pass)
        add_message('invalid password')
      elsif pass1 != pass2
        add_message('new passwords are not same')
      else
        @current_account.plain_password = pass1
        @current_account.save!
        add_message('password changed')
      end
    end
  end

  def devices
  end

  def notifications
  end

  def profile
  end

  def applications
  end

  class Form::Account
    include ActiveModel::Model

    attr_accessor :name, :contacts, :password, :language, :timezone

    validates :name, length: { minimum: 1, maximum: 15 }
    # validates :emails, format: { with: /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/i }
    validates :password, length: { minimum: 1 }
    validates :language, presence: true
    validates :timezone, presence: true
  end
end
