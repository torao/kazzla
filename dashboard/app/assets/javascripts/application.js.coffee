# This is a manifest file that'll be compiled into application.js, which will include all the files
# listed below.
#
# Any JavaScript/Coffee file within this directory, lib/assets/javascripts, vendor/assets/javascripts,
# or vendor/assets/javascripts of plugins, if any, can be referenced here using a relative path.
#
# It's not advisable to add code directly here, but if you do, it'll appear at the bottom of the
# compiled file.
#
# Read Sprockets README (https://github.com/sstephenson/sprockets#sprockets-directives) for details
# about supported directives.
#
#= require jquery
#= require jquery_ujs
#= require turbolinks
#= require_tree .

# 日時の標準形式を取得
@date_string = (date) ->
	year = date.getYear();
	month = date.getMonth()
	day = date.getDate();
	hour = date.getHours();
	minute = date.getMinutes();
	second = date.getSeconds();
	milli = date.getTime() % 1000;
	if year < 1000
		year += 1900
	month += 1
	year + "-" + ("0" + month).slice(-2) + "-" + ("0" + day).slice(-2) + "T" + ("0" + hour).slice(-2) + ":" + ("0" + minute).slice(-2) + ("0" + second).slice(-2) + "." + ("00" + milli).slice(-3);

# 未読通知件数の設定
@set_unread_notification_count = (count) ->
  $('#unread_notification_count').text(count)