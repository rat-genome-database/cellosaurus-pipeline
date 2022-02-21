#!/usr/bin/env bash
#
# Cellosaurus disease annotation pipeline
#
. /etc/profile
APPNAME=CellosaurusPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/$APPNAME.jar --annotator "$@" > annot.log 2>&1

mailx -s "[$SERVER] Cellosaurus Annotation Pipeline Run" mtutaj@mcw.edu < $APPDIR/logs/annotSummary.log
