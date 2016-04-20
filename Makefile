rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

SERVER_JAR=server/target/scala-2.10/server-assembly-0.1.0.jar
INGEST_JAR=ingest/target/scala-2.10/ingest-assembly-0.1.0.jar

${SERVER_JAR}: $(call rwildcard, server/src, *.scala) server/build.sbt
	./sbt "project server" assembly
	touch -m ${SERVER_JAR}

${INGEST_JAR}: $(call rwildcard, ingest/src, *.scala) ingest/build.sbt
	./sbt "project ingest" assembly
	touch -m ${INGEST_JAR}

.upload-code: ${SERVER_JAR} ${INGEST_JAR} scripts/emr/*
	scripts/upload-code.sh
	touch .upload-code

upload-code: .upload-code

create-cluster: .upload-code
	cd scripts && ./create-cluster.sh

start-ingest: .upload-code
	scripts/start-ingest.sh --cluster-id=$(CLUSTER_ID) --polygon=$(POLYGON)

clean:
	./sbt clean
	rm .upload-code
