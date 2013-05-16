class CreateNodeNodes < ActiveRecord::Migration
  def change
    create_table :node_nodes do |t|
      t.string :name, :null => false
      t.string :uuid, :null => false
      t.integer :region_id, :null => false
      t.string :continent, :null => false
      t.string :country
      t.string :state
      t.float :latitude
      t.float :longitude
      t.string :agent
      t.float :qos
      t.string :status
      t.text :public_key, :null => false
      t.string :public_addresses, :null => false	# comma separated values
      t.timestamp :disconnected_at

      t.timestamps
    end
  end
end
