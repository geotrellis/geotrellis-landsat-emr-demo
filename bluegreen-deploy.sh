#!/usr/bin/env bash

function cluster_color() {
    COLOR=$( aws emr describe-cluster --cluster-id $1 --output text \
        | egrep "TAGS\s+Color" \
        | cut -f3 \
        | tr "[:upper:]" "[:lower:]" )

    if [ $COLOR != "" ] ; then
        echo "$1:$COLOR"
    fi
}

# Find active clusters with my name
IDS=$(aws emr list-clusters --active --output text \
    | egrep CLUSTERS \
    | egrep "$NAME" \
    | cut -f2)

# List only clusters that have a color
COLORS=$( for ID in $IDS; do cluster_color $ID; done; )

# Verify there is only one color up
COUNT=$(wc -w <<< $COLORS)
if [[ $COUNT -gt 1 ]]; then
    echo "Multiple active clusters named '$NAME':"
    echo $COLORS
    exit 1
fi

# Pick a new color and note old cluster id
if [[ $COUNT -gt 0 ]]; then
    OLD_CLUSTER=$(cut -f1 -d: <<< $COLORS)
    OLD_COLOR=$(cut -f2 -d: <<< $COLORS)
    if [ $OLD_COLOR = "blue" ]; then
        NEW_COLOR="green"
    elif [ $OLD_COLOR = "green" ]; then
        NEW_COLOR="blue"
    else
        echo "Active cluster named '$NAME' is neither green nor blue, but: '$OLD_COLOR'"
    fi
else
    NEW_COLOR="green"
fi

set -x
# Deploy the next color and wait for ingest to finish before taking down old cluster
make -e COLOR=$NEW_COLOR create-cluster
make -e start-ingest || (make -e terminate-cluster && exit 1)
make -e wait || (make -e terminate-cluster && exit 1)
make -e update-route53 || (exit 1)
if [ -v $OLD_CLUSTER ]; then
    make -e CLUSTER_ID=$OLD_CLUSTER terminate-cluster
fi
