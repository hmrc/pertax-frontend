# Portal rehoming environment

# Prerequisites

* If using Linux, please ensure you are running at least **16.04 LTS**
* The local portal requires HMRC VPN access and MongoDB. If having problems with MongoDB, please refer to **Additional GOTCHAS** for installation troubleshooting.  

Install Docker and Maven using the following guides: 

* [Docker](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04)  
**Important - You need to be a member of the Docker group to execute Docker commands without Sudo. Follow Step 2 on the above link**
* [Maven](https://howtoprogram.xyz/2016/09/08/install-maven-ubuntu-16-04-lts-xenial-xerus/)

Create a directory named “portal-environment” within your “hmrc-development-environment" directory

Navigate into the new `portal-environment` directory

# prh-oracle-xe  
In the "portal-environment" directory, clone the repo:  
[https://github.com/hmrc/prh-oracle-xe](https://github.com/hmrc/prh-oracle-xe)   
 
* Navigate into the `prh-oracle-xe` directory  
* `source ./helpers.sh`  
* `docker pull philipharries/oracle-xe`  
* `docker tag philipharries/oracle-xe:latest oracle-xe:latest`  
* `start_database sa-filing-1617 1523`  
* Wait for 30 seconds  
* `databases/sa-filing-1617/setup.sh`  

this will then create the database  

**GOTCHAs**  
If a container is reported to be in use then:    

List docker containers  
`docker ps -a`
  
`docker stop <container id> & docker rm <container id>`    
 
then re-run  

* `databases/sa-filing-1617/setup.sh`  

# prh-activemq  
In the "portal-environment" directory, clone the repo:  
[https://github.tools.tax.service.gov.uk/HMRC/prh-activemq](https://github.tools.tax.service.gov.uk/HMRC/prh-activemq)    

* Navigate into the `prh-activemq` directory
* `./build`
* `./run`

# portal-content-proxy  
In the "portal-environment" directory, clone the repo:  
[https://github.tools.tax.service.gov.uk/HMRC/portal-content-proxy](https://github.tools.tax.service.gov.uk/HMRC/portal-content-proxy)  

* Navigate into the `portal-content-proxy` directory
* `./build.sh`
* `./start-docker.sh`

**GOTCHAs**  
If you get the following error: "docker: Error response from daemon: Conflict. The container name "/portal-content-stub" is already in use by container "2a5a36990097f716c6e6b252639004dfd8df9e791eb4b6b98dff60267938c445". You have to remove (or rename) that container to be able to reuse that name.  

List docker containers  
`docker ps -a`    

Look for container named `portal-content-stub`, make a note of the container id  

`docker rm <container id>`  

then re-run  

* `./build.sh`
* `./start-docker.sh`

# portal-nginx  
In the "portal-environment" directory, clone the repo:  
[https://github.com/nicf82/portal-nginx](https://github.com/nicf82/portal-nginx)

* Navigate into the `portal-nginx` directory
* `sm --start $(cat services.txt) -f` - *ensure you are connected to the HMRC VPN*
* `./build.sh`
* `./run.sh`

# sa-filing-1617
In the "portal-environment" directory, clone the repo:  
[https://github.tools.tax.service.gov.uk/HMRC/sa-filing-1617](https://github.tools.tax.service.gov.uk/HMRC/sa-filing-1617)  

* Navigate into the `sa-filing-1617/sa-filing-1617-service` directory - *Please note the additional directory depth*  
* `mvn -DDOCKER_IP=127.0.0.1 -Ptomcat tomcat7:run-war`
* wait for `INFO: Starting ProtocolHandler ["http-bio-8080"]` to be displayed in the terminal window. The local portal is now initialised 
 
# Viewing the local portal

open [the local portal](https://localhost/auth-login-stub/sign-in?continue=%2Fself-assessment-file%2F1617%2Find%2F1097172564%2Freturn%2Fwelcome) and use auth-login-stub with confidence level strong and 200, the SAUTR as 1097172564 and any valid Nino  

# Additional Notes
When you have already followed all of the above steps and are restarting, the following commands may be required to kill and remove the existing containers for docker that are left running:

`docker kill $(docker ps -q)`  
`docker rm $(docker ps -a -q)`

Also, TomCat instances need to be removed:

`pkill -9 -f tomcat `

# Additional GOTCHAS
**You may need to disable your firewall with the following linux command**

   `sudo ufw disable`

**You may also need to enable an exception in browser for insecure SSH or click "Advanced" and then click continue to localhost**

IF YOU HAVE AN ERROR PAGE WITH "THE FRONTEND STUBBED SERVICE FAILED WITH THE FOLLOWING REASON. PLEASE TRY AGAIN LATER" 

This can be a problem with your MongoDB version and installation. If you are on Ubuntu 16.04 LTS then run these commands:

* `sudo rm /etc/apt/sources.list.d/mongodb-org-X.X.list`
* `sudo dpkg -l | grep mongo`
* `sudo apt-get remove mongodb* --purge`
* `sudo apt-get autoremove`
* `sudo rm -r -f /var/lib/mongodb/`
* `sudo rm -r -f /var/log/mongodb/`
* `cd $HOME`

then follow the steps in this following guide to cleanly [reinstall Mongo for Ubuntu 16.04](https://www.rosehosting.com/blog/how-to-install-mongodb-on-ubuntu-16-04/)

# Starting the portal from a single command

* Copy the `startLocalPortal.sh` file to the `hmrc-development-environment/portal-environment` directory.
* edit the file, replacing the **prhoraclexe**, **prhactivemq**, **portalcontentproxy**, **safiling1617service** and **portalnginx** directory variables with the locations you cloned the relevant git repos to on your local machine.
* `chmod +x startLocalPortal.sh`
* `./startLocalPortal.sh`  
* wait for `INFO: Starting ProtocolHandler ["http-bio-8080"]` to be displayed in the terminal window. The local portal is now initialised  
 
open [the local portal](https://localhost/auth-login-stub/sign-in?continue=%2Fself-assessment-file%2F1617%2Find%2F1097172564%2Freturn%2Fwelcome) and use auth-login-stub with confidence level strong and 200, the SAUTR as 1097172564 and any valid Nino  

# Stopping the portal from a single command

* Copy the `stopLocalPortal.sh` file to the `hmrc-development-environment/portal-environment` directory.
* edit the file, replacing the **portalnginx** directory variable with the location you cloned the relevant git repo to on your local machine.
* `chmod +x startLocalPortal.sh`
* `./stopLocalPortal.sh`
