#!/bin/sh

for i in "$@"
do
case $i in
    --cluster-id=*)
        CLUSTER_ID="${i#*=}"
        shift;;
    --bbox=*)
        BBOX="${i#*=}"
        shift;;
    --start-date=*)
        START_DATE="${i#*=}"
        shift;;
    --end-date=*)
        END_DATE="${i#*=}"
        shift;;
esac
done

if [[ -z $CLUSTER_ID || -z $BBOX || -z $START_DATE || -z $END_DATE ]]
then
  echo "usage: start-ingest --cluster-id=<CLUSTER_ID> --BBOX=<xmin,ymin,xmax,ymax> --start-date=yyyy-mm-dd --end-date=yyyy-mm-dd"
  exit
fi

ARGS="spark-submit,--master,yarn-cluster,\
--class,demo.LandsatIngestMain,\
--driver-memory,${EXECUTOR_MEMORY:-5G},\
--executor-cores,${EXECUTOR_CORES:-2},\
--executor-memory,${EXECUTOR_MEMORY:-5G},\
--conf,spark.dynamicAllocation.enabled=true,\
--conf,spark.yarn.executor.memoryOverhead=${YARN_OVERHEAD:-256},\
--conf,spark.yarn.driver.memoryOverhead=${YARN_OVERHEAD:-256},\
$EMR_TARGET/ingest-assembly-0.1.0.jar,\
--layerName,landsat,\
--bbox,\"$BBOX\",\
--startDate,$START_DATE,\
--endDate,$END_DATE,\
--output,accumulo,\
--params,\"instance=accumulo,table=tiles,user=root,password=secret\""

set -x
aws emr add-steps --output text --cluster-id $CLUSTER_ID \
    --steps Type=CUSTOM_JAR,Name=Ingest,Jar=command-runner.jar,Args=[$ARGS]
