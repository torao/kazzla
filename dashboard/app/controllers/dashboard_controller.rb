# -*- encoding: UTF-8 -*-
#
class DashboardController < ApplicationController
  before_filter :signin_required, :expect => [ :index, :lang ]

	def index
		if not signin?
      @signin = Form::SignIn.new
      @signup = Form::SignUp.new
      render :action => "top"
		elsif @current_account.hashed_password.empty?
			redirect_to "/auth/change_password"
    end
  end

	# set user language who is not signed-in
	def lang
		lang = params[:lang]
		if ! lang.nil? and Code::Language.available_language?(lang)
			cookies[:lang] = { :value => lang, :path => "/", :expires => 10.years.from_now }
		end
		redirect_to "/"
	end

	def home
		render :layout => nil
	end

	def status
		render :layout => nil
	end

	def nodes
    id = params[:id]
    if request.get?
      if id.blank?
        @nodes = @current_account.nodes
      else
        @edit = true unless params[:edit].nil?
        @nodes = @current_account.nodes.select{|n| n.uuid == id } || [ ]
      end
    elsif request.put?
      if id.blank?
        render_not_found
      else
        nodes = @current_account.nodes.select{|n| n.uuid == id }
        if nodes.empty?
          render_not_found
        else
          n = params[:node_node]
          nodes[0].name = n[:name]
          nodes[0].continent = n[:continent]
          nodes[0].country = n[:country]
          nodes[0].state = n[:state]
          nodes[0].latitude = n[:latitude]
          nodes[0].longitude = n[:longitude]
          nodes[0].save!
          redirect_to :action => :nodes, :id => id
        end
      end
    else
      render_method_not_allowed
    end
	end

end
