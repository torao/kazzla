class SettingsController < ApplicationController
  before_filter :signin_required

  def account
    if request.get?
      @account = Form::Account.new({
        name: @current_account.name,
        contacts: @current_account.contacts.map{ |c| Form::Contact.create(c) },
        language: @current_account.language,
        timezone: @current_account.timezone,
      })
    elsif request.post?
      contacts = params[:contacts].nil? ? []: params[:contacts].values.map{ |c| Form::Contact.new(c) }
      contacts = contacts.push(Form::Contact.new(params[:new_contact])) unless params[:new_contact][:uri].blank?
      # ・認証済みのコンタクト情報については変更不可 (削除のみ)
      # ・最低一つの認証済みコンタクト情報が必要
      @account = Form::Account.new(params[:account])
      @account.name = @current_account.name
      # ・既存のコンタクト情報について、自分が所有していないコンタクトIDを上書きしないように抽出
      existing_contact_ids = @current_account.contacts.map{ |c| c.id.to_s }
      @account.contacts = contacts.select{ |c| c.id.nil? || existing_contact_ids.include?(c.id.to_s) }
      @current_account.language = @account.language
      @current_account.timezone = @account.timezone
      @current_account.contacts = @account.contacts.map{ |c|
        if c.id.nil?
          c.to_model
        else
          contact = @current_account.contacts.find{ |d| d.id.to_s == c.id.to_s }
          if contact.confirmed_at.nil?
            contact.schema = c.schema
            contact.uri = c.uri
          end
          contact
        end
      }
      @current_account.save!
      # 新規追加されIDが振られたコンタクト情報を復元
      @account.contacts = @current_account.contacts.map{ |c| Form::Contact.create(c) }
    end
  end

  # 連絡先の有効性確認
  def confirm_contact
    if request.get?
      token = Auth::Token.where(['token=?', params[:token]]).first
      if token.nil? or token.expired_at < Time.now or token.scheme != Auth::Token::SCHEME_CONFIRM_MAIL_ADDRESS
        # トークンが無効
        @message = { alert: :warning, message: :invalid_token }
        unless token.nil?
          token.destroy
        end
      elsif @current_account.nil? or @current_account.id != token.account_id
        # パスワードを入力して
        unless session[:account_id].nil?
          eventlog("sign-out success")
        end
        reset_session
        @message = { alert: :danger, message: :not_logged_on }
      else
        # メールアドレスの確認完了: トークンの削除してコンタクト情報を確認済みにする
        contact = Auth::Contact.find(token.object)
        contact.confirmed_at = Time.now
        ActiveRecord::Base.transaction {
          contact.save!
          token.destroy!
        }
        @message = { alert: :success, message: :your_mail_address_is_verified }
      end
    elsif request.post?
      # コンタクト情報確認手続きの開始
      contact = Auth::Contact.find(params[:id])
      contact.confirm(url_for({ :controller => :settings, :action => :confirm_contact, :id => '' }))
      @message = { alert: :success, message: :confirm_message_sent_to_your_mail_address }
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

  # 公開プロフィール情報の編集
  def profile
    # 現在のプロフィール取得
    profile = @current_account.profile
    if profile.nil?
      profile = Auth::Profile.new({ account_id: @current_account.id })
      profile.save!
    end
    if request.get?
      # プロフィール表示
      @profile = Form::Profile.new({
        account_id: @current_account.id,
        name: profile.name,
        bio: profile.bio,
        location: profile.location,
        url: profile.url
      })
    elsif request.post?
      # プロフィール更新
      @profile = Form::Profile.new(params[:profile])
      @profile.account_id = @current_account.id
      profile.name = @profile.name
      profile.bio = @profile.bio
      profile.location = @profile.location
      profile.url = @profile.url
      ActiveRecord::Base.transaction {
        profile.save!
        unless @profile.image.nil?
          icon = Auth::ProfileImage.where(['account_id=?', @current_account]).first
          if icon.nil?
            icon = Auth::ProfileImage.new({ account_id: @current_account.id })
          end
          icon.content = @profile.image.read
          icon.content_type = @profile.image.content_type
          icon.original_name = @profile.image.original_filename
          icon.save!
        end
      }
    end
  end

  def applications
  end

  class Form::Account
    include ActiveModel::Model

    attr_accessor :name, :contacts, :password, :language, :timezone

    validates :name, length: { minimum: 1, maximum: 15 }
    # validates :emails, format: { with: /\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\Z/i }
    validates :password, length: { minimum: 1 }
    validates :language, presence: true
    validates :timezone, presence: true
  end
end
