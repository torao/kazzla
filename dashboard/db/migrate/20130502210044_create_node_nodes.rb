class CreateNodeNodes < ActiveRecord::Migration
  def change
    create_table :node_nodes do |t|
		  t.integer :account_id, :null => false
      t.string :uuid, :null => false
			t.string :name
      t.integer :region_id
      t.string :continent
      t.string :country
      t.string :state
      t.float :latitude
      t.float :longitude
      t.string :agent
      t.float :qos
      t.string :status
      t.binary :certificate, :null => false
      t.timestamp :disconnected_at

      t.timestamps
    end
		add_index :node_nodes, :uuid, :unique => true
  end
end
