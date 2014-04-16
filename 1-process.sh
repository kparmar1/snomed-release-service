#!/bin/sh

IFS=$'\n'

effectiveDate="20140131"
outputFile="der2_Refset_SimpleFull_INT_20140131.txt"
refsetIdLookupFile="refset-identifiers.txt"
createSnapshotDuplicate=true

# Fresh target directory
echo 'Creating fresh target directory'
rm -rf target
mkdir target
cp source/* target

echo 'Processing files'

# Replace spaces in filenames with underscore
for file in `find target -type f`; do mv $file `echo $file | sed 's/ /_/g'`; done

# Remove header
for file in `find target -type f`; do tail -n +2 $file > tmp && mv tmp $file; done

# Remove any extra columns and replace EffectiveDate
for file in `find target -type f`; do
	cat $file | awk -v effectiveDate=$effectiveDate '{print $1"\t"effectiveDate"\t"$3"\t"$4"\t"$5"\t"$6}' > tmp
	mv tmp $file
done

# Replacing first column with random UUID
for file in `find target -type f`; do
	echo "Generating random UUIDs for $file"
	for line in `cat $file`; do
		randomUUID=`uuidgen | tr '[:upper:]' '[:lower:]'`
		echo $line | awk -v uuid=$randomUUID '{print uuid"\t"$2"\t"$3"\t"$4"\t"$5"\t"$6}' >> tmp
	done
	mv tmp $file
done
echo

# Replace refset identifiers
if [ -f $refsetIdLookupFile ]; then
	for file in `find target -type f`; do
		filename=`basename $file`
		refsetId=`grep $filename $refsetIdLookupFile | awk '{print $2}'`
		if [ ! -s $refsetId ]; then
			echo "$filename refsetId = $refsetId"
			cat $file | awk -v refset=$refsetId '{print $1"\t"$2"\t"$3"\t"$4"\t"refset"\t"$6}' > tmp
		else
			echo "Found no matching refset id for file $filename in refset lookup file $refsetIdLookupFile"
		fi
		mv tmp $file
	done
else
	echo "$refsetIdLookupFile file missing."
	exit 1
fi
echo

# Insert correct header
echo 'id	effectiveTime	active	moduleId	refSetId	referencedComponentId' > $outputFile
# Combine files
cat `find target -type f` >> $outputFile
mv $outputFile target

# Windows line breaks CR LF
for file in `find target -type f`; do
	sed $'s/$/\r/' $file > tmp
	mv tmp $file
done

# UTF-8

if ( $createSnapshotDuplicate ); then
	snapshotFile=`echo $outputFile | sed 's/Full/Snapshot/'`
	cp "target/$outputFile" "target/$snapshotFile"
fi
