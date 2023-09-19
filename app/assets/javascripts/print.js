const printlink = document.getElementById('printLink');

if(printlink != null && printlink != 'undefined' ) {

    printlink.addEventListener("click", function (e) {
        e.preventDefault();
        window.print();
    });
};
