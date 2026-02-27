#!/bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [ "$1" == "add" ]; then
    SOURCE_FILE="math-add.rs"
elif [ "$1" == "multiply" ]; then
    SOURCE_FILE="math-multiply.rs"
else
    echo "Usage: $0 {add|multiply}"
    echo "  add      - Build math.wasm with addition operation"
    echo "  multiply - Build math.wasm with multiplication operation"
    exit 1
fi

(
    cd ${SCRIPT_DIR}
    docker build --build-arg SOURCE_FILE=${SOURCE_FILE} . -t build_rs_math
    docker run --rm build_rs_math > math.wasm.tmp && mv math.wasm.tmp math.wasm
    echo "Built math.wasm from ${SOURCE_FILE}"
)
