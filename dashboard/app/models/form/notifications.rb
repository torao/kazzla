# -*- encoding: UTF-8 -*-
#
class Form::Notifications
  include ActiveModel::Model

  attr_accessor :notifications, :total, :page, :items_per_page

  def last_page
    page_count - 1
  end

  def page_count
    (self.total / self.items_per_page) - ((total % items_per_page == 0)? 1: 0) + 1
  end

  def first?
    self.page.to_i <= 0
  end

  def last?
    self.page.to_i >= last_page
  end

  def paginate(max_links)
    ml2 = max_links / 2
    (if page_count <= max_links
      (0 .. last_page)
    elsif page <= ml2.ceil
      (0 .. max_links - 1)
    elsif page >= page_count - ml2.ceil
      (last_page - max_links + 1 .. last_page)
    else
      ((page - ml2.floor) .. (page + ml2.floor))
    end).each{ |i| yield i, i == self.page }
  end

end

