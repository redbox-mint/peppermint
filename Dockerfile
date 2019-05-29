FROM openjdk:8-jdk-slim

ENV APP_HOME /opt/peppermint
ENV MEM_START 512m
ENV MEM_MAX 512m
EXPOSE 8080

COPY gradlew $APP_HOME/
COPY gradle $APP_HOME/gradle
COPY build.gradle $APP_HOME/
COPY settings.gradle $APP_HOME/
COPY src $APP_HOME/src

COPY scripts/ $APP_HOME/scripts/
COPY config.json $APP_HOME/

COPY support $APP_HOME/support
RUN cd $APP_HOME && chmod +x support/prep-run.sh && support/prep-run.sh
RUN chmod +x $APP_HOME/support/docker-entrypoint.sh
RUN cd $APP_HOME && ./gradlew shadowJar

WORKDIR $APP_HOME

ENTRYPOINT ["support/docker-entrypoint.sh"]
