class Node::Node < ActiveRecord::Base
  attr_accessible :agent, :continent, :country, :disconnected_at, :latitude, :longitude, :name, :public_addresses, :public_key, :qos, :region_id, :state, :status, :uuid
end
