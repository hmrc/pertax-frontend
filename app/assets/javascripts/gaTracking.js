$(".trackLink").click(function() {
  ga('send', 'event', 'outbound', 'click', $(this).attr("href"));
});

$( ".send-tcs-ga-event" ).submit(function( event ) {
  if ( $(this).find( "#value-true" ).prop("checked") ){
    ga('send', 'event', 'outbound', 'click', $(this).data("tcs-ga-event-url"));
  }
});

$(".ga-track-event").click(function(event) {
  if ( $(this).is('a') && $(this).attr('target') != '_blank') {
    event.preventDefault();

    var redirectUrl = $(this).attr('href');

    gaWithCallback('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'), function() {
      window.location.href = redirectUrl;
    });

  } else {
    ga('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'));

    var gaEventCategoryValue = $(this).attr('data-ga-event-category');
    if( gaEventCategoryValue.includes('expand') ) {
      $(this).attr('data-ga-event-category', gaEventCategoryValue.replace('expand', 'hide'));
      $(this).data('ga-event-category', $(this).attr('data-ga-event-category'));
    } else if( gaEventCategoryValue.includes('hide') ) {
      $(this).attr('data-ga-event-category', gaEventCategoryValue.replace('hide', 'expand'));
      $(this).data('ga-event-category', $(this).attr('data-ga-event-category'));
    }
  }
});

function gaWithCallback(send, event, category, action, label, callback) {
  ga(send, event, category, action, label, {
    hitCallback: gaCallback
  });
  var gaCallbackCalled = false;
  setTimeout(gaCallback, 5000);

  function gaCallback() {
    if(!gaCallbackCalled) {
      callback();
      gaCallbackCalled = true;
    }
  }
}
