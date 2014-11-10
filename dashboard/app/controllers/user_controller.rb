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

  def list
    render :text => "[{id:'torao',name:'Torao Takami'}]"
  end
end
