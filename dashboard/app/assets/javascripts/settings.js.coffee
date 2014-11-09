# Place all the behaviors and hooks related to the matching controller here.
# All this logic will automatically be available in application.js.
# You can use CoffeeScript in this file: http://jashkenas.github.com/coffee-script/

$ ->
  console.log('init: contacts=' + $('.delete-contact').size())
  $('.delete-contact').click (e) ->
    id = $(e.target).closest('a').data('id')
    $('#contact_' + id).slideUp('fast', () ->
      $('#contact_' + id).remove()
      console.log('delete contact: ' + id)
    )
    false
  @
