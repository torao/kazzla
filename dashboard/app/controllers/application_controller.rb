class ApplicationController < ActionController::Base
  # Prevent CSRF attacks by raising an exception.
  # For APIs, you may want to use :null_session instead.
  protect_from_forgery with: :exception

  def signin?
    if @_signin.nil?
      if session[:account_id].nil?
        @_signin = false
      else
        @current_account = Auth::Account.find_by_id(session[:account_id])
        if @current_account.nil?
          reset_session
          @_signin = false
        else
          @_signin = true
        end
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
    logger.debug('### ' + msg)
    p '### ' + msg
    log = Activity::Eventlog.new
    log.account_id = session[:account_id]
    log.level = 0
    log.code = 0
    log.remote = request.remote_ip
    log.message = msg
    log.save
  end

  def not_found
    render :file => "#{Rails.root}/public/404.html", :status=>'404 Not Found'
  end

  private

  def signin_required
    if not signin? and request.path != '/'
      redirect_to '/'
    end
  end

  def render_not_found
    render :file => "#{Rails.env}/public/404.html", :status => '404 Not Found'
  end

  def render_method_not_allowed
    render :file => "#{Rails.env}/public/405.html", :status => '405 Method Not Allowed'
  end
end
