# -*- encoding: UTF-8 -*-
#
require "kazzla"
include Kazzla

class AuthController < ApplicationController
  before_filter :signin_required, :only => [ :change_password, :withdraw ]

  # サインアップ
	def signup
    if request.post?
      @signup = Form::SignUp.new(params[:form_sign_up])
      if ! @signup.language.nil? && @signup.valid?
        # アカウント情報作成
        account = Auth::Account.new
        account.plain_password = @signup.password
        account.name = @signup.name
        account.language = @signup.language
        account.timezone = @signup.timezone
        # コンタクト情報作成
        contact = Auth::Contact.new
        contact.account = account
        contact.schema = "mailto"
        contact.uri = @signup.email.downcase
        # アカウント情報、コンタクト情報を保存
        ActiveRecord::Base.transaction do
          unless account.save && contact.save
            raise ActiveRecord::Rollback, 'save failed'
          end
        end
        # ログインの実行
        session[:account_id] = account.id
        eventlog('sign-up success')
        redirect_to '/'
      else
        # 入力エラーやトップページからのサインアップは追加の入力ページを表示
        render :signup
      end
    end
  end

  # 退会
  def withdraw
    if request.post?
      @withdraw = Form::Withdraw.new(params[:withdraw])
      if @withdraw.valid?
        account_id = session[:account_id]
        unless account_id.nil?
          account = Auth::Account.find(account_id)
          account.destroy
          reset_session
        end
        redirect_to '/'
      end
    else
      @withdraw = Form::Withdraw.new({ confirmed: false })
    end
  end

  def signin
		# request via reset_password
		unless params[:ticket].nil?
			issue = Auth::PasswordResetSecret.find(:first,
				:conditions => [ "secret=? and issued_at>?", params[:ticket], Time::now-(24*60*60) ])
			unless issue.nil?
				account = issue.account
				account.hashed_password = ""
				account.save()
				session[:account_id] = account.id
				issue.destroy()
				redirect_to "/"
				eventlog("sign-in success to reset password")
			else
				add_message "invalid ticket or expired"
				render :action => :reset_password
				eventlog("ticket invalid or expired")
			end
			return
		end

    if request.post?
      @signin = Form::SignIn.new(params[:form_sign_in])
      if @signin.valid?

        # password authentication with ID
        account = Auth::Account.where(['name=?', @signin.account]).first
        if ! account.nil? and account.authenticate(@signin.password)
          session[:account_id] = account.id
          redirect_to '/'
          eventlog("sign-in success")
          return
        end

        # password authentication with email
        contact = Auth::Contact.where(['uri=? and schema=\'mailto\'', @signin.account.downcase]).first
        if not contact.nil? and contact.account.authenticate(@signin.password)
          account = contact.account
          account.save!
          session[:account_id] = account.id
          redirect_to "/"
          eventlog("sign-in success")
          return
        end
      end

      # authentication failure
      eventlog("sign-in failure")
      reset_session
    end
  end

  def signout
		unless session[:account_id].nil?
			eventlog("sign-out success")
		end
    reset_session
    redirect_to "/"
  end

	def change_password
		if not signin?
			redirect_to "/"
		elsif request.post?
			current_account.plain_password = params[:password]
			current_account.save!
			eventlog("password changed")
			redirect_to "/"
		end
	end

	def reset_password
		if request.post?
			address = params[:account].downcase
			contact = Auth::Contact.find_by_uri("mailto:" + address)
			unless contact.nil?
				issue = Auth::PasswordResetSecret.new
				issue.secret = random_string(128)
				issue.account = contact.account
				issue.issued_at = Time::now
				url = request.url + "/../signin?ticket=" + issue.secret
				Message.reset_password(address, url).deliver
				issue.save()
				eventlog("send reset-password mail to: #{address}, ticket: #{issue.secret}")
			end
			add_message("Send e-Mail to specified address that contains URL to reset password (This message is shown for incorrect address for security reason).")
		end
  end

	private

	def random_string(length)
		chars = ("a".."z").to_a + ("A".."Z").to_a + ("0".."9").to_a
		result = ""
		length.times do
			result << chars[rand(chars.length)]
		end
		result
  end

end
