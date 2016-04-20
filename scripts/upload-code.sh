#!/bin/sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

# upload Accumulo/GeoWAve bootstrap script
aws s3 cp $DIR/emr/bootstrap-geowave.sh $EMR_TARGET/
aws s3 cp $DIR/emr/geowave-install-lib.sh $EMR_TARGET/
aws s3 cp $DIR/emr/wait-for-accumulo.sh $EMR_TARGET/
aws s3 cp $DIR/emr/tile-server.sh $EMR_TARGET/

# upload ingest assembly
aws s3 cp $DIR/../ingest/target/scala-2.10/ingest-assembly-0.1.0.jar $EMR_TARGET/
aws s3 cp $DIR/../server/target/scala-2.10/server-assembly-0.1.0.jar $EMR_TARGET/
