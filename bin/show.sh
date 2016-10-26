#!/usr/bin/env bash -x
. `dirname "${BASH_SOURCE[0]}"`/setup.sh

java -Xmx${memory_requirement} -cp ${DLVA_JAR} -Dlogback.configurationFile=${SLF4J_CONFIG}   \
    org.campagnelab.dl.varanalysis.tools.Show ${other_parameters}