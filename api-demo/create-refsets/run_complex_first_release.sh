#!/bin/bash
#
# Sets up variables to be consumed by create-refsets

# Stop on error
set -e;

# Declare run specific parameters
inputFilesDir="complex-first"
unwantedInputFilesDir="simple-subsequent"
manifestFileName="manifest_complex_20140131.xml"
effectiveDate="2014-01-31"
isFirstTime=true
firstTimeStr="true"

#Pass control to our busy worker bee
calling_program=`basename $0`
source create-refsets.sh