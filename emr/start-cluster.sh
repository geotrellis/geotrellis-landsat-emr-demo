# Starts a long running ETL cluster.

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

aws emr create-cluster \
  --name "Landsat Ingest Init Test" \
  --region $AWS_REGION \
  --release-label emr-4.5.0 \
  --use-default-roles \
  --ec2-attributes KeyName=$KEY_NAME \
  --applications Name=Ganglia Name=Hadoop Name=Hue Name=Spark Name=Zeppelin-Sandbox \
  --instance-groups \
    Name=Master,InstanceCount=1,InstanceGroupType=MASTER,InstanceType=$MASTER_INSTANCE \
    Name=Workers,InstanceCount=$WORKER_COUNT,BidPrice=$WORKER_PRICE,InstanceGroupType=CORE,InstanceType=$WORKER_INSTANCE \
  --bootstrap-action Path=$EMR_TARGET/4/bootstrap-geowave.sh,Name=Bootstrap_GeoWave_Node \
  --steps Type=CUSTOM_JAR,Name=Wait,Jar=s3://elasticmapreduce/libs/script-runner/script-runner.jar,Args=["s3:$EMR_TARGET/wait.sh"]
