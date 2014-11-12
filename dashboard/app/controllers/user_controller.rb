class UserController < ApplicationController
  before_filter :sign_in_if_possible, :expect => [ :profile_image ]

  # ユーザプロフィールの表示
  def profile
    id = params[:id]
    account = Auth::Account.where(['name=?', id]).first
    if account.nil?
      account = Auth::Account.where(['id=?', id]).first
    end
    if account.nil?
      not_found
    elsif account.profile.nil?
      @profile = Form::UserProfile.new({ account_id: account.id, user_name: account.name })
    else
      @profile = Form::UserProfile.new({
        account_id: account.id,
        user_name: account.name,
        display_name: account.profile.name,
        bio: account.profile.bio,
        location: account.profile.location,
        url: account.profile.url,
      })
    end
  end

  # プロフィールイメージの参照
  def profile_image
    img = Auth::ProfileImage.where(['account_id=?', params[:id]]).first
    if img.nil?
      redirect_to '/default-profile.png'
    else
      send_data img.content, :type => img.content_type, :disposition => 'inline'
    end
  end

  # 通知の表示
  def notifications
    if request.get?
      page = params[:p].nil? ? 0: params[:p].to_i
      items_per_page = params[:ipp].nil? ? 25: params[:ipp].to_i
      if page <= 0
        page = 0
      elsif items_per_page > 100
        items_per_page = 100
      end
      @notifications = Form::Notifications.new({
        notifications: User::Notification.where(['account_id=?', @current_account.id]).order('created_at desc').offset(page * items_per_page).limit(items_per_page),
        total: @current_account.notifications_count,
        page: page,
        items_per_page: items_per_page
      })
    elsif request.post?
      User::Notification.make_items_to_read(@current_account.id, params[:id])
      render :json => { id: [ params[:id] ], unread: @current_account.unread_notifications_count }
    end
  end

  def list
    render :text => "[{id:'torao',name:'Torao Takami'}]"
  end
end
