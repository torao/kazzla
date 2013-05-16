class CreateNodeRegions < ActiveRecord::Migration
  def change
    create_table :node_regions do |t|
      t.string :name, :null => false
      t.string :continent
      t.string :country
      t.string :state

      t.timestamps
    end
  end
end
