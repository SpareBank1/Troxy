FROM adoptopenjdk/openjdk8:jdk8u222-b10
MAINTAINER Stian Conradsen "stian.conradsen@sparebank1.no"

RUN mkdir -p /opt/troxy/bin \
             /opt/troxy/lib  \
             /opt/troxy/conf \
             /opt/troxy/logs  \
             /opt/troxy/data/filters \
             /opt/troxy/data/recordings  && \
    chmod -R a+w /opt/troxy/data/recordings/ && \
    useradd --system --user-group troxy

USER troxy:troxy

COPY ./server/target/server-*.jar /opt/troxy/lib/
COPY ./server/target/dependency/* /opt/troxy/lib/
COPY ./filter/target/filter-*.jar /opt/troxy/data/filter/

COPY ./local/conf/troxy.properties /opt/troxy/conf/troxy.properties

WORKDIR /opt/troxy

EXPOSE 8080 443

ENV JAVA_OPTS "-Dtroxy.home=/opt/troxy -XX:+HeapDumpOnOutOfMemoryError"

ENTRYPOINT ["/bin/bash", "-O", "extglob", "-c", "java $JAVA_OPTS -jar lib/server-+([0-9]).+([0-9]).+([a-z0-9])?(-SNAPSHOT).jar"]

