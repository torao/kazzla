class CreateAuthProfiles < ActiveRecord::Migration
  def change
    create_table :auth_profiles do |t|
      t.integer :account_id, :null => false # アカウントID
      t.string  :name, :null => false, :default => ''       # 表示名
      t.text    :bio, :null => false, :default => ''        # 略歴
      t.string  :location, :null => false, :default => ''   # 場所
      t.string  :url, :null => false, :default => ''        # ホームページ

      t.timestamps
    end
    add_index(:auth_profiles, :account_id, { :unique => true })
  end
end
