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
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar --annotator "$@" > annot.log 2>&1

mailx -s "[$SERVER] Cellosaurus Annotation Pipeline Run" mtutaj@mcw.edu < $APPDIR/logs/annotSummary.log
