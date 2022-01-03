ARG DOCKER_URL
ARG DOCKER_ORG

FROM ${DOCKER_URL}/${DOCKER_ORG}/common-img:latest

# Custom build from here on
ENV PROJECT_NAME cambada

COPY --chown=build:build src src
COPY --chown=build:build deps.edn deps-orig.edn
COPY --chown=build:build pom.xml pom.xml



ARG BUILD_ID

RUN set -e &&\
    cat deps-orig.edn | envsubst > deps.edn &&\
    clj -M:jar &&\
    ls -la target &&\
    cp pom.xml /dist/release-libs/${PROJECT_NAME}-1.0.${BUILD_ID}.jar.pom.xml &&\
    cp target/${PROJECT_NAME}-1.0.${BUILD_ID}.jar /dist/release-libs/${PROJECT_NAME}-1.0.${BUILD_ID}.jar &&\
    ls -la target &&\
    rm -rf ~/.m2/repository


RUN cat pom.xml
