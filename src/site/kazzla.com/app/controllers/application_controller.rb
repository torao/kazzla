class ApplicationController < ActionController::Base
  protect_from_forgery

  def signin?
    ! session[:account_id].nil?
  end

  def current_user
		Auth::Account.find(session[:account_id])
  end

end
