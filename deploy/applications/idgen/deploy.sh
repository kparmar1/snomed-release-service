#!/bin/bash
set -e

config_dir=$1
tmp="tmp"

webapps="/var/lib/tomcat7/webapps"
axis_url="http://apache.mirrors.timporter.net//axis/axis2/java/core/1.6.2/axis2-1.6.2-war.zip"
axis_zip="axis2.zip"
axis_war="axis2.war"

aar_url="http://build.snomedtools.org/job/snomed-release-system/ws/id-generation/id-generation-ws/target/idgen.aar"
aar_file="idgen.aar"
axis_services="/var/lib/tomcat7/webapps/axis2/WEB-INF/services/"


echo "* Checking Axis2 war is installed"
if [ ! -f $webapps/axis2.war ]; then
	echo "* Downloading Axis2 war"
	wget $axis_url -O $axis_zip
	unzip $axix_zip $axis_war
	mv $axis_war $webapps
else
	echo "* Axis2 is there"
fi

echo "* Downloading latest $aar_file"
wget $aar_url

if [ "$config_dir" != "" ]; then
	echo "* Appying config"
	rm -rf config
	cp -r ../$config_dir config
	cd config
	zip -r ../$aar_file *
	cd ..
else
	echo "* No config to apply"
fi

mv $aar_file $axis_services