class UserController < ApplicationController
  def list
    render :text => "[{id:'torao',name:'Torao Takami'}]"
  end
end
