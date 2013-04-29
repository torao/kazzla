class ApplicationController < ActionController::Base
  protect_from_forgery

  def signin?
    ! session[:account_id].nil?
  end

  def current_account
		if @current_account_.nil?
			@current_account_ = Auth::Account.find(session[:account_id])
		end
		@current_account_
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
