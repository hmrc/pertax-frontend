#### pertax-frontend 

This is the repository for the Personal Tax Account (PTA) frontend project.

#### Integrating the PTA header into your project

Example Partial URL:
```conf
//personal-account/integration/main-content-header? \
  name=John%20Smith& \
  lastLogin=1444229760085& \
  item_text=Home& \
  item_url=%2F& \
  item_text=Profile& \
  item_url=%2Fprofile& \
  showBetaBanner=true
```

Add in your `application.conf` like this:
```conf
header-service {
  headerPartial = "%s/personal-account/integration/main-content-header"
}
```

Partial URL parameters:

| Parameter      | Type    | Example       | Description                                     |
|----------------|---------|---------------|-------------------------------------------------|
| name           | string  | John Smith    | Name of the logged in user                      |
| lastLogin      | number  | 1444229760085 | Time in milliseconds of last login              |
| item_text      | string  | Home          | Link text for breadcrumb item (repeatable)      |
| item_url       | string  | /             | Link location for breadcrumb item (repeatable)  |
| showBetaBanner | boolean | true/false    | Should the beta banner be displayed             |
| deskProToken   | string  | PTA           | Token defined by DeskPro to denote your service |

## To run locally

```sh
## Update service manager config.
cd $WORKSPACE/service-manager-config && git pull

## Prepare MongoDB for take-off by increasing `ulimit` … 🚀
sudo launchctl limit maxfiles 65536 200000

## Clone the repo.
cd $WORKSPACE
git clone git@github.com:hmrc/pertax-frontend.git

## Start the services and stop the frontend.
sm --start PTA_ALL -r
sm --stop PERTAX_FRONTEND

## Run tests and then run the service.
cd $WORKSPACE/pertax-frontend
sbt -mem 6699 test
sbt -mem 6699 'run 9232'

## Service is now running at http://localhost:9232/personal-account/
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
