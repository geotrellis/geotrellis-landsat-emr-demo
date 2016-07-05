#!/usr/bin/env bash

# Bootstrap docker Cassandra on EMR cluster
for i in "$@"
do
    case $i in
        --continue)
            CONTINUE=true
            shift ;;
        *)
            ;;
    esac
done

is_master() {
    if [ $(jq '.isMaster' /mnt/var/lib/info/instance.json) = 'true' ]; then
        return 0
    else
        return 1
    fi
}

if [ ! $CONTINUE ]; then
    sudo yum -y install docker
    sudo usermod -aG docker hadoop
    sudo service docker start

    THIS_SCRIPT="$(realpath "${BASH_SOURCE[0]}")"
	  TIMEOUT= is_master && TIMEOUT=3 || TIMEOUT=4
	  echo "bash -x $THIS_SCRIPT --continue > /tmp/cassandra-bootstrap.log" | at now + $TIMEOUT min
	  exit 0 # Bail and let EMR finish initializing
fi

MASTER_IP=$(xmllint --xpath "//property[name='yarn.resourcemanager.hostname']/value/text()"  /etc/hadoop/conf/yarn-site.xml)

sudo mkdir -p /mnt/cassandra
sudo docker run --name=cassandra -d --net=host \
     -v /mnt/cassandra:/var/lib/cassandra \
     -e CASSANDRA_SEEDS=${MASTER_IP} \
     cassandra:2
