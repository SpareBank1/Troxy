FROM ubuntu:latest
MAINTAINER Stian Conradsen "stian.conradsen@sparebank1.no"

RUN apt-get update
RUN apt-get install -y wim
