#!/bin/sh

# Taken from https://github.com/radanalyticsio/openshift-spark/blob/2.4/modules/common/added/scripts/entrypoint#L50
# OpenShift passes random UID and spark requires it to be present in /etc/passwd
function patch_uid {
    # Check whether there is a passwd entry for the container UID
    myuid=$(id -u)
    mygid=$(id -g)
    uidentry=$(getent passwd $myuid)

    # If there is no passwd entry for the container UID, attempt to create one
    if [ -z "$uidentry" ] ; then
        if [ -w /etc/passwd ] ; then
            echo "$myuid:x:$myuid:$mygid:anonymous uid:${PWD}:/bin/false" >> /etc/passwd
        else
            echo "Container ENTRYPOINT failed to add passwd entry for anonymous UID"
        fi
    fi
}

patch_uid

exec  "$@"
exit $?
