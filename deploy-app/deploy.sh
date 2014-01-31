#!/bin/bash
set -e
cd $(dirname "$0");
pwd

# Check params
if [ $# -eq 1 ]; then

	app=$1
	host=`hostname`

	echo "* Choosing $app config for $host"
	config_dir="hosts/${host}/${app}"
	if [ -d $config_dir ]; then
		echo "* Found host specific config for this app."
	else
		config_dir="host/*/${app}"
		if [ -d $config_dir ]; then
			echo "* Found generic host config for this app."
		else
			config_dir=""
			echo "* No config overrides found for this app."
		fi
	fi

	deploy_script="applications/${app}/deploy.sh"
	tmp="tmp"
	if [ -f $deploy_script ]; then
		rm -rf $tmp
		mkdir $tmp
		cd $tmp
		../$deploy_script $config_dir
		cd -
		rm -rf $tmp
	else
		echo "* Deploy script for application '$app' not found."
	fi

else

	echo "Usage: ./deploy.sh [app_name]"
	exit 1
fi