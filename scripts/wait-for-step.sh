
#!/bin/sh

for i in "$@"
do
    case $i in
        --cluster-id=*)
            CLUSTER_ID="${i#*=}"
            shift
            ;;
        --step-id=*)
            STEP_ID="${i#*=}"
            shift
            ;;
    esac
done

if [[ -z $CLUSTER_ID || -z $STEP_ID ]]
then
    echo "usage: wait-for-step.sh --cluster-id=<CLUSTER_ID> --step-id=<STEP_ID>"
    exit
fi

while (true); do
    OUT=$(aws emr describe-step --cluster-id $CLUSTER_ID --step-id $STEP_ID | tee step-status.json)

    if [[ $OUT =~ (\"State\": \"([A-Z]+)\") ]]; then
        STATE=${BASH_REMATCH[2]}
    else
        STATE=READ_ERROR
    fi
    echo $STATE
    case $STATE in
        PENDING | RUNNING)
            sleep 60;;
        COMPLETED)
            exit 0;;
        CANCELLED | FAILED | INTERRUPTED)
            exit 1;;
        *)
            exit 2;;
    esac
done
