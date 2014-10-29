class SettingsController < ApplicationController
  before_filter :signin_required

  def account
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
end
