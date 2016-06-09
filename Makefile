export AWS_DEFAULT_REGION := us-east-1
export S3_URI := s3://geotrellis-test/emr
export EC2_KEY := geotrellis-cluster

export WORKER_COUNT := 1
export WORKER_INSTANCE:=m3.2xlarge
export WORKER_PRICE := 0.15
export MASTER_INSTANCE:=m3.xlarge
export MASTER_PRICE := 0.15
export EXECUTOR_MEMORY := 4900M
export EXECUTOR_CORES := 2
export YARN_OVERHEAD := 500

export BBOX := -98.77,36.12,-91.93,41.48

SERVER_ASSEMBLY := server/target/scala-2.10/server-assembly-0.1.0.jar
INGEST_ASSEMBLY := ingest/target/scala-2.10/ingest-assembly-0.1.0.jar
SCRIPT_RUNNER := s3://elasticmapreduce/libs/script-runner/script-runner.jar

# Define functions to read cluster and step ids if they're not in the environment
CID = $(shell echo $${CLUSTER_ID:-$$(< cluster-id.txt)})
SID = $(shell echo $${STEP_ID:-$$(< last-step-id.txt)})

rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

${SERVER_ASSEMBLY}: $(call rwildcard, server/src, *.scala) server/build.sbt
	./sbt "project server" assembly
	@touch -m ${SERVER_ASSEMBLY}

${INGEST_ASSEMBLY}: $(call rwildcard, ingest/src, *.scala) ingest/build.sbt
	./sbt "project ingest" assembly
	@touch -m ${INGEST_ASSEMBLY}

upload-code: ${SERVER_ASSEMBLY} ${INGEST_ASSEMBLY} scripts/emr/*
	@aws s3 cp $(CURDIR)/scripts/emr/bootstrap-demo.sh ${S3_URI}/
	@aws s3 cp $(CURDIR)/scripts/emr/bootstrap-geowave.sh ${S3_URI}/
	@aws s3 cp $(CURDIR)/scripts/emr/geowave-install-lib.sh ${S3_URI}/
# @aws s3 cp $(CURDIR)/scripts/emr/wait-for-accumulo.sh ${S3_URI}/
# @aws s3 cp $(CURDIR)/scripts/emr/tile-server.sh ${S3_URI}/
	@aws s3 cp ${SERVER_ASSEMBLY} ${S3_URI}/
	@aws s3 cp ${INGEST_ASSEMBLY} ${S3_URI}/

upload-site:
	@cd viewer && npm run build
	tar -czf viewer/site.tgz -C $(CURDIR)/viewer/dist .
	@aws s3 cp viewer/site.tgz ${S3_URI}/

create-cluster:
	aws emr create-cluster --name "Landsat Ingest" \
--release-label emr-4.5.0 \
--output text \
--use-default-roles \
--configurations file://$(CURDIR)/scripts/configurations.json \
--log-uri ${S3_URI}/logs \
--ec2-attributes KeyName=${EC2_KEY} \
--applications Name=Ganglia Name=Hadoop Name=Hue Name=Spark Name=Zeppelin-Sandbox \
--instance-groups \
Name=Master,InstanceCount=1,InstanceGroupType=MASTER,InstanceType=${MASTER_INSTANCE} \
Name=Workers,InstanceCount=${WORKER_COUNT},BidPrice=${WORKER_PRICE},InstanceGroupType=CORE,InstanceType=${WORKER_INSTANCE} \
--bootstrap-actions \
Name=BootstrapGeoWave,Path=${S3_URI}/bootstrap-geowave.sh \
| tee cluster-id.txt

# Name=BootstrapDemo,Path=${S3_URI}/bootstrap-demo.sh,\
# Args=[--tsj=${S3_URI}/server-assembly-0.1.0.jar,--tsm=demo.Main,--site=${S3_URI}/site.tgz]
start-ingest:
	@if [ -z $$START_DATE ]; then echo "START_DATE is not set" && exit 1; fi
	@if [ -z $$END_DATE ]; then echo "END_DATE is not set" && exit 1; fi

	aws emr add-steps --output text --cluster-id ${CID} \
--steps Type=CUSTOM_JAR,Name=Ingest,Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,demo.LandsatIngestMain,\
--driver-memory,${EXECUTOR_MEMORY},\
--executor-memory,${EXECUTOR_MEMORY},\
--executor-cores,${EXECUTOR_CORES},\
--conf,spark.dynamicAllocation.enabled=true,\
--conf,spark.yarn.executor.memoryOverhead=${YARN_OVERHEAD},\
--conf,spark.yarn.driver.memoryOverhead=${YARN_OVERHEAD},\
${S3_URI}/ingest-assembly-0.1.0.jar,\
--layerName,landsat,\
--bbox,\"${BBOX}\",\
--startDate,${START_DATE},\
--endDate,${END_DATE},\
--output,accumulo,\
--params,\"instance=accumulo,table=tiles,user=root,password=secret\"\
] | cut -f2 | tee last-step-id.txt

wait-for-step:
	scripts/wait-for-step.sh --cluster-id=${CID} --step-id=${SID}

terminate-cluster:
	aws emr terminate-clusters --cluster-ids ${CID}
	rm -f cluster-id.txt
	rm -f last-step-id.txt

clean:
	./sbt clean
