class UserController < ApplicationController

  # ユーザプロフィールの表示
  def profile

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
