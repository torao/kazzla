# -*- coding: utf-8 -*-

module Kazzla 
	
	def random_string(length)
    chars = ("a".."z").to_a + ("A".."Z").to_a + ("0".."9").to_a
    result = ""
    length.times do
      result << chars[rand(chars.length)]
    end
    result
  end

end

