#!/bin/bash
set -e

if [ -z "${environmentName}" ]
then
	echo "No environment specific file detected, running as local"
else 
	filename="${environmentName}-data-service.properties"
fi 

if [ -z "$apiPort" ]
then
	apiPort=8080
fi

function getProperty() {
	property=$1	
	cat ${propertiesFile} | grep ${property} | awk '{print $2}'
}

while getopts ":dsp:" opt
do
	case $opt in
		d) 
			debugMode=true
			echo "Option set to start API in debug mode.  Listening on port 8000"
			debugFlags="-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -Djava.compiler=NONE" 
		;;
		s) 
			skipMode=true
			echo "Option set to skip build."
		;;
		p)
			apiPort=$OPTARG
			echo "Option set run API on port ${apiPort}"
		;;
		help|\?)
			echo -e "Usage: [-d]  [-p <port>]"
			echo -e "\t d - debug. Starts the API in debug mode, which an IDE can attach to on port 8000"
			echo -e "\t p <port> - Starts the API on a specific port (default 8888)"
			echo -e "\t s - skip.  Skips the build"
			exit 0
		;;
	esac
done

if [ -n "${filename}" ]
then 
	propertiesFile="`pwd`/../data-service/target/classes/${filename}"
	if [ -f "$propertiesFile" ]; then
	
		if [ -z "${skipMode}" ] 
		then 
			echo 'Building API webapp (skipping tests)..'
			sleep 1
			mvn -f ../pom.xml clean install -Dapple.awt.UIElement='true' -DskipTests=true
			echo
		fi
	
		echo "Starting API webapp using $environmentName environment on port ${apiPort}."
		echo
		sleep 1
		java ${debugFlags} -Xmx4g -DENV_NAME=$(whoami) -jar target/exec-api.jar -DdataServicePropertiesPath="file://${propertiesFile}"  -httpPort=${apiPort}
	else
		echo "You don't have access to the $environmentName environment (missing properties file? Was looking for ${propertiesFile})."
		echo
		exit 1
	fi
else 
	java ${debugFlags} -Xmx4g -DENV_NAME=$(whoami) -jar target/exec-api.jar  -httpPort=${apiPort}
fi
