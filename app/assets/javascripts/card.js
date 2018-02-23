
var card = (function () {

  // Capture when a user clicks
  $('.card-body').on('click', function () {
    var url = $(this).find('a').attr('href')
    if (url !== undefined) {
      window.location.href = url
    }
  })

  var checkSize = function () {
    var maxHeight = getMaxHeight('.card-body')
    setMaxheight('.card-body', maxHeight)
  }

  // Check each card. If the card does not contain a .card-action
  // make .card-body full height
  var fullHeight = function () {
    var cardEle = $('.card').not(':has(.card-action)')
    if (cardEle.length > 0) {
      cardEle.each(function () {
        var $cardBody = $(this).children('.card-body')
        var maxHeight = getMaxHeight('.card')
        var paddingTop = $cardBody.css('padding-top').replace('px', '')
        var paddingBottom = $cardBody.css('padding-bottom').replace('px', '')
        var totalHeight = maxHeight - paddingTop - paddingBottom
        $cardBody.css('border-bottom', '0')
        setMaxheight($cardBody, totalHeight)
      })
    }
  }

  isNotMobile(checkSize)
  $(window).resize(isNotMobile(checkSize))

  isNotMobile(fullHeight)
  $(window).resize(isNotMobile(fullHeight))

  // set max height for any collection of elements
  function setMaxheight (ele, maxHeight) {
    $(ele).height(maxHeight)
  }

  // get max height for any collection of elements
  function getMaxHeight (ele) {
    var height = []
    $(ele).each(function () {
      if ($(this).height() > 0)
        height.push($(this).height())
    })
    var maxHeight = height.sort(function (a, b) { return b - a })[0]
    return maxHeight
  }

  // Only run fucntion if the screen size is not mobile.
  function isNotMobile (func) {
    if (navigator.appVersion.indexOf('MSIE 10') === -1) {

      if ($('.card').css('flex-basis') !== '100%') {
        return func()
      }
    }
  }

})()

var doc = document.documentElement
doc.setAttribute('data-useragent', navigator.userAgent)
