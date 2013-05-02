class DashboardController < ApplicationController

	def index
		if not signin?
      render :action => "top"
		elsif @current_account.hashed_password.empty?
			redirect_to "/auth/change_password"
    end
  end

	# set user language who is not signed-in
	def lang
		lang = params[:lang]
		if ! lang.nil? and Code::Language.available_language?(lang)
			cookies[:lang] = { :value => lang, :path => "/", :expires => 1.years.from_now }
		end
		redirect_to "/"
	end

end
