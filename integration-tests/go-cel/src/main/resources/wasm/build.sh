#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

(
    cd ${SCRIPT_DIR}
    docker build -t go-cel-builder .
    docker run --rm go-cel-builder > go-cel.wasm.tmp && mv go-cel.wasm.tmp go-cel.wasm
)
