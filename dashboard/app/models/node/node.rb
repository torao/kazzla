require 'openssl'
require 'digest/md5'

class Node::Node < ActiveRecord::Base
#  attr_accessible :account_id, :agent, :continent, :country, :certificate, :disconnected_at, :latitude, :longitude, :name, :qos, :region_id, :state, :status, :uuid

  def session
    @session ||= Node::Session.find_by_node_id(uuid)
  end

  def cert
    OpenSSL::X509::Certificate.new(certificate)
  end

  def cert_fingerprint
    Digest::MD5.hexdigest(certificate)
  end

  def country_name
    c = Code::Country.country(country)
    if c.nil?
      ""
    else
      c.name
    end
  end

  def continent_name
    c = Code::Continent.continent(continent)
    if c.nil?
      ""
    else
      c.name
    end
  end

end
