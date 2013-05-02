class ApplicationController < ActionController::Base
  protect_from_forgery

  def signin?
		if @_signin.nil?
			unless session[:account_id].nil?
				@current_account = Auth::Account.find_by_id(session[:account_id])
				if @current_account.nil?
					reset_session
					@_signin = false
				else
					@_signin = true
				end
			else
				@_signin = false
			end
		end
		@_signin
  end

  def current_account
		signin?
		@current_account
  end

	def add_message(msg)
		if @messages.nil?
			@messages = [ ]
		end
		@messages.push(msg)
	end

	def eventlog(msg)
		log = Activity::Eventlog.new
		log.account_id = session[:account_id]
		log.level = 0
		log.code = 0
		log.remote = request.remote_ip
		log.message = msg
		log.save()
	end

end
