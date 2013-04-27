require "auth"

class AuthController < ApplicationController
  def signup
    if request.post?
      account = Auth::Account.new
      account.plain_password = params[:password]
      account.name = ""
      account.locale = params[:locale]
      account.timezone = params[:timezone]
      account.last_login = Time::now
      contact = Auth::Contact.new
      contact.account = account
      contact.uri = "mailto:" + params[:account].downcase
      account.save()
      contact.save()
      session[:account_id] = account.id
      redirect_to "/"
    end
  end

  def withdraw
    account_id = session[:account_id]
    unless account_id.nil?
      account = Auth::Account.find(account_id)
      account.destroy()
      reset_session
      redirect_to "/"
    end
  end

  def signin
    contact = Auth::Contact.find_by_uri("mailto:" + params[:account].downcase)
    if ! contact.nil? and contact.account.authenticate(params[:password])
			account = contact.account
			account.last_login = Time::now
			account.save()
      session[:account_id] = account.id
      redirect_to "/"
    else
      reset_session
    end
  end

  def signout
    reset_session
    redirect_to "/"
  end
end
