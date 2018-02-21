portalnginx=/home/richardb/Applications/hmrc-development-environment/portal-environment/portal-nginx

printf "Killing required services"
while true;do echo -n .;sleep 1;done &
sleep 5
kill $!; trap 'kill $!' SIGTERM
printf "\r\n"

cd $portalnginx
sm --stop $(cat services.txt) -f

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
