#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

(
    cd ${SCRIPT_DIR}
    docker build . -t build_rs_operation
    docker run --rm build_rs_operation > operation.wasm.tmp && mv operation.wasm.tmp operation.wasm
)
