class CreateAuthRoles < ActiveRecord::Migration
  def change
    create_table :auth_roles do |t|
      t.string :name, :null => false
			t.string :permissions, :null => false		# comma separated symbols

      t.timestamps
    end
  end
end
