#### pertax-frontend 

This is the repository for the Personal Tax Account (PTA) frontend project.

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
sm2 --start PTA_ALL -r
sm2 --stop PERTAX_FRONTEND

## Run tests and then run the service.
cd $WORKSPACE/pertax-frontend
sbt -mem 6699 test
sbt -mem 6699 'run 9232'

## Service is now running at http://localhost:9232/personal-account/
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
