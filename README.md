pertax-frontend
===============

This is the repository for the Personal Tax Account front end project. 



Integrating the PTA header into your project
--------------------------------------------

Example Partial URL: 

  //personal-account/integration/main-content-header?name=John%20Smith&lastLogin=1444229760085&item_text=Home&item_url=%2F&item_text=Profile&item_url=%2Fprofile&showBetaBanner=true

Partial URL parameters:

| Parameter      | Type    | Example       | Description                                     |
|----------------|---------|---------------|-------------------------------------------------|
| name           | string  | John Smith    | Name of the logged in user                      |
| lastLogin      | number  | 1444229760085 | Time in milliseconds of last login              |
| item_text      | string  | Home          | Link text for breadcrumb item (repeatable)      |
| item_url       | string  | /             | Link location for breadcrumb item (repeatable)  |
| showBetaBanner | boolean | true/false    | Should the beta banner be displayed             |
| deskProToken   | string  | PTA           | Token defined by DeskPro to denote your service |

Installing sass
---------------

    sudo apt-get install ruby
    sudo apt-get install nodejs-legacy 
    

    npm install
    sudo gem install sass 
    sudo gem install scss_lint
    
License
--------

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

