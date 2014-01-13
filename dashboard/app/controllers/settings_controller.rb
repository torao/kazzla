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
