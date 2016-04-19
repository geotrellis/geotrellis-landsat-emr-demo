# Starts a long running ETL cluster.

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

ARGS=spark-submit,--master,yarn-cluster
ARGS=$ARGS,--class,demo.LandsatIngestMain
ARGS=$ARGS,--driver-memory,10G
ARGS=$ARGS,--executor-cores,4
ARGS=$ARGS,--executor-memory,10G
ARGS=$ARGS,--conf,spark.dynamicAllocation.enabled=true
ARGS=$ARGS,--conf,spark.yarn.executor.memoryOverhead=512
ARGS=$ARGS,--conf,spark.yarn.driver.memoryOverhead=512
ARGS=$ARGS,$EMR_TARGET/ingest.jar
ARGS=$ARGS,--layerName,landsat
ARGS=$ARGS,--polygonUri,$EMR_TARGET/tristate.json
ARGS=$ARGS,--limit,4
ARGS=$ARGS,--output,accumulo
ARGS=$ARGS,--params,\"instance=accumulo,table=tiles,user=root,password=secret\"

aws emr add-steps --cluster-id $CLUSTER_ID --steps Type=CUSTOM_JAR,Name=Ingest,Jar=command-runner.jar,Args=[$ARGS]
