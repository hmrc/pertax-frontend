prhoraclexe=/home/richardb/Applications/hmrc-development-environment/portal-environment/prh-oracle-xe
prhactivemq=/home/richardb/Applications/hmrc-development-environment/portal-environment/prh-activemq
portalcontentproxy=/home/richardb/Applications/hmrc-development-environment/portal-environment/portal-content-proxy
safiling1617service=/home/richardb/Applications/hmrc-development-environment/portal-environment/sa-filing-1617/sa-filing-1617-service
portalnginx=/home/richardb/Applications/hmrc-development-environment/portal-environment/portal-nginx

# portalnginx build.sh and run.sh must be executable! chmod +x build.sh & chmod +x run.sh
#
# local portal will be created at: -
#
# https://localhost/auth-login-stub/sign-in?continue=%2Fself-assessment-file%2F1617%2Find%2F1097172564%2Freturn?lang=eng

clear
read -n 1 -s -r -p "Make sure your VPN is connected - Press any key to continue"
clear

printf "Starting required services"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"

cd $portalnginx
# sm --start $(cat services.txt) -f > /dev/null & (background)
sm --start $(cat services.txt) -f

printf "Killing Tomcat instances"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
pkill -9 -f tomcat

printf "killing Docker containers"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"

docker kill $(docker ps -q)
docker rm $(docker ps -a -q)

printf "Creating and starting initial Docker database"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
source $prhoraclexe/helpers.sh
docker pull philipharries/oracle-xe
docker tag philipharries/oracle-xe:latest oracle-xe:latest
start_database sa-filing-1617 1523

printf "Waiting for database creation (30 seconds)"
while true;do echo -n .;sleep 1;done &
sleep 30
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
$prhoraclexe/databases/sa-filing-1617/setup.sh

printf "Building and running activemq"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
cd $prhactivemq
./build
./run

printf "Building portal-content-proxy and starting Docker"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
cd $portalcontentproxy
./build.sh
./start-docker.sh

printf "Starting and running portal-nginx"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
cd $portalnginx
./build.sh
./run.sh

if which xdg-open > /dev/null
then
  xdg-open https://localhost/auth-login-stub/sign-in?continue=%2Fself-assessment-file%2F1617%2Find%2F1097172564%2Freturn?lang=eng
elif which gnome-open > /dev/null
then
  gnome-open https://localhost/auth-login-stub/sign-in?continue=%2Fself-assessment-file%2F1617%2Find%2F1097172564%2Freturn?lang=eng
fi

printf "Starting sa-filing-1617-service"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"
cd $safiling1617service 
mvn -DDOCKER_IP=127.0.0.1 -Ptomcat tomcat7:run-war
