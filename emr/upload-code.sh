#!/bin/sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

# upload Accumulo/GeoWAve bootstrap script
aws s3 cp $DIR/bootstrap-geowave.sh $EMR_TARGET/bootstrap-geowave.sh
aws s3 cp $DIR/geowave-install-lib.sh $EMR_TARGET/geowave-install-lib.sh
aws s3 cp $DIR/wait.sh $EMR_TARGET/wait.sh

# upload ingest assembly
aws s3 cp $DIR/../ingest/target/scala-2.10/ingest-assembly-0.1.0.jar $CODE_TARGET/ingest-assembly-0.1.0.jar
