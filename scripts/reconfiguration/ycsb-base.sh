confi#!/bin/bash

# ---------------------------------------------------------------------

trap onexit 1 2 3 15
function onexit() {
    local exit_status=${1:-$?}
    exit $exit_status
}

# ---------------------------------------------------------------------

DATA_DIR="y-out"
FABRIC_TYPE="ssh"
FIRST_PARAM_OFFSET=0

EXP_TYPES=( \
    "reconfig-2b --partitions=4 --benchmark-size=10000000  --exp-suffix=y-s10000000-4" \
    "reconfig-2b --partitions=8 --benchmark-size=10000000  --exp-suffix=y-s10000000-8" \
    "reconfig-2b --partitions=8 --benchmark-size=20000000  --exp-suffix=y-s20000000-8" \
    "reconfig-2b --partitions=12 --benchmark-size=20000000  --exp-suffix=y-s20000000-12" \
    "reconfig-2b --partitions=12 --benchmark-size=4000000  --exp-suffix=y-s4000000-12" \
    
)

#for b in smallbank tpcc seats; do
for b in ycsb; do
# for b in seats; do
    PARAMS=( \
        --no-update \
        --results-dir=$DATA_DIR \
        --benchmark=$b \
        --stop-on-error \
        --exp-trials=1 \
        --exp-attempts=1 \        
        --no-json \
	    --client.interval=10000 \
        --client.output_interval=true \
        --client.duration=280000 \
        --client.warmup=30000 \
        --client.output_results_csv=interval_res.csv
    )
    
    i=0
    cnt=${#EXP_TYPES[@]}
    while [ "$i" -lt "$cnt" ]; do
        ./experiment-runner.py $FABRIC_TYPE ${PARAMS[@]:$FIRST_PARAM_OFFSET} \
            --exp-type=${EXP_TYPES[$i]}
        FIRST_PARAM_OFFSET=0
        i=`expr $i + 1`
    done

done
