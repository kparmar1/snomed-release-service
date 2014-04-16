#!/bin/sh

expectedColumnNames="id	effectiveTime	active	moduleId	refSetId	referencedComponentId"

file=`find target -name '*SimpleFull*'`
echo "File is $file"

#
# FUNCTIONS
#
function columnRegex {
	col=$1
	regex=$2
	echo "Testing column $col (`getColumnName $col`) against a regex"
	awk -v col=$col '{print $col}' $file | tail -n +2 | grep -E -v "${regex}" > regex-bad-cols.txt
	if [ -s regex-bad-cols.txt ]; then
		echo "The following values in column $col do not match the regex \"$regex\""
		cat regex-bad-cols.txt
		exit 1;
	else
		rm regex-bad-cols.txt
	fi
}

function uniqueValues {
	echo "Testing column $col (`getColumnName $col`) values are unique"
	col=$1
	awk -v col=$col '{print $col}' $file | tail -n +2 | sort | uniq -d > non-uniq-cols.txt
	if [ -s non-uniq-cols.txt ]; then
		echo "The following values are not unique in column 1"
		cat non-uniq-cols.txt
		exit 1
	else
		rm non-uniq-cols.txt
	fi
}

function getColumnName {
	col=$1
	echo $expectedColumnNames | awk -v col=$col '{print $col}'
}

#
# TESTS
#

# Column based tests
echo 'Running tests'

# Check column names
echo 'Testing column names'
colNames=`head -n1 $file | tr -d '\n' | tr -d '\r'`
if [ "$colNames" != "id	effectiveTime	active	moduleId	refSetId	referencedComponentId" ]; then
	echo "Column names not as expected"
	exit 1
fi

# Column 1, id = UUID and unique
columnRegex 1 '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
uniqueValues 1

# Column 2, effectiveTime = date
columnRegex 2 '((19|20)\d\d)(0?[1-9]|1[012])(0?[1-9]|[12][0-9]|3[01])'

# Column 3, active = 1 or 0
columnRegex 3 '[10]'

# Column 4, moduleId = 900000000000207008
columnRegex 4 '900000000000207008'

# Column 5, refsetId = one of a set
columnRegex 5 '450974004|450976002|450977006|450978001|450980007|450981006|450982004|450983009|450984003|450985002|450986001|450988000|450989008|450990004|450991000|450992007'

# Column 6, referencedComponentId = a positive integer
columnRegex 6 '\d+'
columnRegex 6 '\d{6,18}'

echo 'All Tests passed'
