# Place all the behaviors and hooks related to the matching controller here.
# All this logic will automatically be available in application.js.
# You can use CoffeeScript in this file: http://jashkenas.github.com/coffee-script/

String::startsWith ?= (s) -> @[...s.length] is s
String::endsWith   ?= (s) -> s is '' or @[-s.length..] is s

# 指定された ID の <select> 要素にデフォルトのタイムゾーンオフセットを設定する。
set_default_timezone_offset = (id) ->
  select = $('#' + id)
  options = $('option', select)
  if $('[selected]', options).size() == 0
    date = new Date()
    offset = date.getTimezoneOffset()
    min = Math.abs(offset / 60)
    sec = Math.abs(offset % 60)
    prefix = (if offset >= 0 then '-' else '+') + ('0' + min).slice(-2) + ":" + ('0' + sec).slice(-2) + ' GMT'
    select = (option) ->
      if option.label.startsWith(prefix)
        $('#' + id).val(option.value)
    select(option) for option in options

$ ->
  # Sign Up 画面でデフォルトのタイムゾーンを設定する
  set_default_timezone_offset('form_sign_up_timezone')
