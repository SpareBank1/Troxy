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

COPY ./local/conf/troxy.properties /opt/troxy/conf/troxy.properties
COPY ./troxy-server/target/troxy-server-*.jar /opt/troxy/lib/
COPY ./troxy-server/target/dependency/* /opt/troxy/lib/
COPY ./filter/target/filter-*.jar /opt/troxy/data/filters/

RUN chown -R troxy /opt/troxy

USER root

WORKDIR /opt/troxy

EXPOSE 8080 443

ENV JAVA_OPTS "-Dtroxy.home=/opt/troxy -XX:+HeapDumpOnOutOfMemoryError"

ENTRYPOINT ["/bin/bash", "-O", "extglob", "-c", "java $JAVA_OPTS -jar lib/troxy-server-*.jar"]

