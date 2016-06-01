rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

export AWS_REGION := us-east-1
export EMR_TARGET := s3://geotrellis-test/emr
export KEY_NAME := geotrellis-cluster

export WORKER_COUNT := 1
export WORKER_INSTANCE:=m3.2xlarge
export WORKER_PRICE := 0.15
export MASTER_INSTANCE:=m3.xlarge
export MASTER_PRICE := 0.15
export BBOX='0,0,100,100'

SERVER_JAR=server/target/scala-2.10/server-assembly-0.1.0.jar
INGEST_JAR=ingest/target/scala-2.10/ingest-assembly-0.1.0.jar

${SERVER_JAR}: $(call rwildcard, server/src, *.scala) server/build.sbt
	./sbt "project server" assembly
	touch -m ${SERVER_JAR}

${INGEST_JAR}: $(call rwildcard, ingest/src, *.scala) ingest/build.sbt
	./sbt "project ingest" assembly
	touch -m ${INGEST_JAR}

upload-code: ${SERVER_JAR} ${INGEST_JAR} scripts/emr/*
	scripts/upload-code.sh

create-cluster:
	cd scripts && source create-cluster.sh | tee cluster-id.txt

start-ingest:
	cd scripts && source start-ingest.sh --cluster-id=$$(<cluster-id.txt) | cut -f2 | tee last-step-id.txt

clean:
	./sbt clean
