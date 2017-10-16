$('input[name=beforeUsingYourPersonalTaxAccount]').click(function() {
  var targetFieldsets = $('fieldset[class*=beforeUsingYourPersonalTaxAccount');
  targetFieldsets.addClass('hidden');
  $('label', targetFieldsets).removeClass('selected');
  $('input', targetFieldsets).prop('checked', false);
  $('fieldset.beforeUsingYourPersonalTaxAccount-'+$(this).val()).removeClass('hidden');
});

$('[class*="checkboxgroup-clear"]').on('change', function() {
  if($(this).is(':checked')) {
    var classes = $(this).attr('class').split(' ');
    for (i in classes) {
      var c = classes[i];
      if ( c.startsWith('checkboxgroup-clear') )
        $('.'+c.replace('-clear', '')+':checked').prop('checked', false).trigger('change');
    }
  }
});

$('[class*="visibility-toggle"]').on('change', function() {
  var classes = $(this).attr('class').split(' ');
  for (i in classes) {
    var c = classes[i];
    if ( c.startsWith('visibility-toggle') )
      $('.'+c.replace('visibility-toggle-', '')).toggleClass('js-hidden').find('input').val('');
  }
});

$(".full-width-banner__close").click(function(){
  $.ajax({
    url: "/personal-account/dismiss-ur-banner",
    success: function(){
      $('.full-width-banner').fadeOut('slow')
    }
  })
});
