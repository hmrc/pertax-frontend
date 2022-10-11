if (typeof accessibleAutocomplete != 'undefined' && document.querySelector('.autocomplete') != null) {
    // load autocomplete
    accessibleAutocomplete.enhanceSelectElement({
        selectElement: document.querySelector('.autocomplete'),
        showAllValues: true
    });

    // =====================================================
    // Polyfill autocomplete once loaded
    // =====================================================
    var checkForLoad = setInterval(checkForAutocompleteLoad, 50);
    var originalSelect = document.querySelector('select.autocomplete');
    var parentForm = upTo(originalSelect, 'form');

    function polyfillAutocomplete(){
        var combo = parentForm.querySelector('[role="combobox"]');
        // =====================================================
        // Update autocomplete once loaded with fallback's aria attributes
        // Ensures hint and error are read out before usage instructions
        // =====================================================
        if(originalSelect && originalSelect.getAttribute('aria-describedby') > ""){
            if(parentForm){
                if(combo){
                    combo.setAttribute('aria-describedby', originalSelect.getAttribute('aria-describedby') + ' ' + combo.getAttribute('aria-describedby'));
                }
            }
        }

        // =====================================================
        // Ensure when user replaces valid answer with a non-valid answer, then valid answer is not retained
        // =====================================================
        var holdSubmit = true;
        parentForm.addEventListener('submit', function(e){
            if(holdSubmit){
                e.preventDefault()
                if(originalSelect.querySelectorAll('[selected]').length > 0 || originalSelect.value > ""){

                    var resetSelect = false;

                    if(originalSelect.value){
                        if(combo.value != originalSelect.querySelector('option[value="' + originalSelect.value +'"]').text){
                            resetSelect = true;
                        }
                    }
                    if(resetSelect){
                        originalSelect.value = "";
                        if(originalSelect.querySelectorAll('[selected]').length > 0){
                            originalSelect.querySelectorAll('[selected]')[0].removeAttribute('selected');
                        }
                    }
                }

                holdSubmit = false;
                //parentForm.submit();
                HTMLFormElement.prototype.submit.call(parentForm); // because submit buttons have id of "submit" which masks the form's natural form.submit() function
            }
        })

    }
    function checkForAutocompleteLoad(){
        if(parentForm.querySelector('[role="combobox"]')){
            clearInterval(checkForLoad)
            polyfillAutocomplete();
        }
    }


}