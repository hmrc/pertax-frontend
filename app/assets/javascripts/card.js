var card = (function () {
  // Capture when a user clicks
  const activeCards = document.querySelectorAll('.card-body' && '.active');
  if(activeCards.length){
       for (let i = 0; i < activeCards.length; i++) {
            activeCards[i].addEventListener('click', function (e) {
            let url = this.querySelector('.card-link').getAttribute('href');
            if (url !== undefined) {
              window.location.href = url;
            }
          });
       }

  }
  var checkSize = function () {
    var maxHeight = getMaxHeight('.card-body');
    setMaxheight('.card-body', maxHeight);
  };

  // Check each card. If the card does not contain a .card-action
  // make .card-body full height
  var fullHeight = function () {
    const parent = document.querySelectorAll('.card');
    for (let i = 0; i < parent.length; i++) {
        if (parent[i].querySelectorAll('.card-action').length == 0) {
            var cardBody = parent[i].querySelector('.card-body');
            var maxHeight = getMaxHeight('.card');
            var totalHeight = maxHeight;
            cardBody.style.height = totalHeight + "px";
            cardBody.style.borderBottom = 0;
        }
    }
  }

  isNotMobile(checkSize);
  window.addEventListener('resize', isNotMobile(checkSize));

  isNotMobile(fullHeight);
  window.addEventListener('resize', isNotMobile(fullHeight));

  // get max height for any collection of elements
  function getMaxHeight(elem) {
    var height = [];
    var allElems = document.querySelectorAll(elem)
    allElems.forEach(function (element, index) {
      if (parseFloat(getComputedStyle(element, null).height.replace("px", "")) > 0) {height.push(parseFloat(getComputedStyle(element, null).height.replace("px", "")))};
    });
    return height.sort(function (a, b) {
      return b - a;
    })[0];
  }

    // set max height for any collection of elements
    function setMaxheight(ele, maxHeight) {

      const allElems = document.querySelectorAll('.card-body');
      allElems.forEach(function (element) {
        element.style.height = maxHeight + "px";
      })
    }

  // Only run fucntion if the screen size is not mobile.
  function isNotMobile(func) {
    if (navigator.appVersion.indexOf('MSIE 10') === -1) {
      if (document.querySelector('.card')){
          let flexbasis = window.getComputedStyle(document.querySelector('.card')).getPropertyValue('flex-basis');
          if (flexbasis !== '100%') {
            return func();
          }
      }
    }
  }
})();

var doc = document.documentElement;
doc.setAttribute('data-useragent', navigator.userAgent);
