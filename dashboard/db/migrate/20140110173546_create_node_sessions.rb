class CreateNodeSessions < ActiveRecord::Migration
  def change
    create_table :node_sessions do |t|
      t.string :session_id        # UUID of Session
      t.string :node_id           # UUID of Node
      t.string :endpoints         # comma separated address and port, such as "192.168.1.100:2222,192.168.1.101:2222"
      t.string :proxy             # internal server address for the node that is not access directly from internet

      t.timestamps
    end
  end
end
