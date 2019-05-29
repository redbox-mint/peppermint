#!/bin/bash
exec /usr/local/openjdk-8/bin/java "-Xms$MEM_START" "-Xmx$MEM_MAX" "-cp" "/root/.sdkman/candidates/groovy/current/lib/*:$APP_HOME/build/libs/peppermint-fat.jar:$APP_HOME" "io.vertx.core.Launcher" &

wait $!
