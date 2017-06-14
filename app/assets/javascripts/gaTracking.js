$(".trackLink").click(function() {
  ga('send', 'event', 'outbound', 'click', $(this).attr("href"));
});

$( ".send-tcs-ga-event" ).submit(function( event ) {
  if ( $(this).find( "#value-true" ).prop("checked") ){
    ga('send', 'event', 'outbound', 'click', $(this).data("tcs-ga-event-url"));
  }
});

$(".ga-track-event").click(function(){
  ga('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'));
});

function gaWithCallback(send, event, category, action, label, func) {
  ga(send, event, category, action, label, {
    hitCallback: callbackFunction
  });
  var callbackFunctionCalled = false;
  setTimeout(callbackFunction, 5000);

  function callbackFunction() {
    if(!callbackFunctionCalled) {
      func();
      callbackFunctionCalled = true;
    }
  }
}
