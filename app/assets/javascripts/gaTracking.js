$(".trackLink").click(function () {
    ga('send', 'event', 'outbound', 'click', $(this).attr("href"));
});

$(".send-tcs-ga-event").submit(function (event) {
    if ($(this).find("#value-true").prop("checked")) {
        ga('send', 'event', 'outbound', 'click', $(this).data("tcs-ga-event-url"));
    }
});

$(".ga-track-anchor-click").click(function (event) {

    var target = $(this).attr('target');
    if ($(this).is('a') && (target == '' || target == '_self')) {
        event.preventDefault();
        var redirectUrl = $(this).attr('href');
        gaWithCallback('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'), function () {
            window.location.href = redirectUrl;
        });

    } else {
        ga('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'));
    }
});

$('.ga-track-accordion-click').click(function (event) {
    var expanded = ($(this).attr('aria-expanded') == 'true');
    ga('send', 'event', 'accordion - ' + (expanded ? 'hide' : 'expand'), $(this).data('ga-event-action'), $(this).data('ga-event-label'));
});

$('.ga-track-error').click(function (event) {
    var radioButtonName = $(this).data('radio-button-name');
    var selectedAnswer = $('input[name=' + radioButtonName + ']:checked').val();
    if (selectedAnswer === undefined) {
        ga('send', 'event', 'error - field', $(this).data('ga-event-action'), "You must select an answer");
    }
});

$('.ga-track-radio-select').click(function (event) {
    var radioButtonName = $(this).data('radio-button-name');
    var selectedAnswer = $('input[name=' + radioButtonName + ']:checked').val();
    if (selectedAnswer !== undefined) {
        ga('send', 'event', 'button - click', $(this).data('ga-event-action'), selectedAnswer);
    }
});

function gaWithCallback(send, event, category, action, label, callback) {
    ga(send, event, category, action, label, {
        hitCallback: gaCallback
    });
    var gaCallbackCalled = false;
    setTimeout(gaCallback, 5000);

    function gaCallback() {
        if (!gaCallbackCalled) {
            callback();
            gaCallbackCalled = true;
        }
    }
}
