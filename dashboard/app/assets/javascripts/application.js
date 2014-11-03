// This is a manifest file that'll be compiled into application.js, which will include all the files
// listed below.
//
// Any JavaScript/Coffee file within this directory, lib/assets/javascripts, vendor/assets/javascripts,
// or vendor/assets/javascripts of plugins, if any, can be referenced here using a relative path.
//
// It's not advisable to add code directly here, but if you do, it'll appear at the bottom of the
// compiled file.
//
// Read Sprockets README (https://github.com/sstephenson/sprockets#sprockets-directives) for details
// about supported directives.
//
//= require jquery
//= require jquery_ujs
//= require turbolinks
//= require_tree .

function date_string(date){
	var year = date.getYear();
	var month = date.getMonth()
	var day = date.getDate();
	var hour = date.getHours();
	var minute = date.getMinutes();
	var second = date.getSeconds();
	var milli = date.getTime() % 1000;
	if(year < 1000){
		year += 1900;
	}
	month ++;
	return year + "-" + ("0" + month).slice(-2) + "-" + ("0" + day).slice(-2) + "T" + ("0" + hour).slice(-2) + ":" + ("0" + minute).slice(-2) + ("0" + second).slice(-2) + "." + ("00" + milli).slice(-3);
}
