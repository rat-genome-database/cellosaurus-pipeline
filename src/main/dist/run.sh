#!/usr/bin/env bash
#
# Cellosaurus loading pipeline
#
. /etc/profile
APPNAME="cellosaurus-pipeline"
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/$APPNAME.jar "$@" > run.log 2>&1

mailx -s "[$SERVER] Cellosaurus Pipeline Run" mtutaj@mcw.edu < $APPDIR/logs/summary.log
