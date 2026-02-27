#!/bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

(
    cd ${SCRIPT_DIR}
    docker build . -t build_go_qrcode
    docker run --rm build_go_qrcode > qr-generator.wasm.tmp && mv qr-generator.wasm.tmp qr-generator.wasm
)
