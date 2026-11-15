#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/policy-tests
javac -encoding UTF-8 -d build/policy-tests app/src/main/java/br/com/espectro/UpdatePolicy.java tests/UpdatePolicyTest.java
java -cp build/policy-tests br.com.espectro.UpdatePolicyTest
