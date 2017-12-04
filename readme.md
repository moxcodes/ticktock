#Ticktock (working title ...)

This is a prototype for a simple time-tracking android component.

The rough idea is that this will manage a periodic, battery-conserving wakeup to
call several `status' checks, including querying GPS, motion sensors, wifi list,
and current media usage. Some of these queries will require elevated
permissions.

The plan is to aggregate these queries, and periodically sync to a server that
will serve as the central data repository for this information.

The rest of the time-tracking system will be written in c++/python, and to be
run on the data repository host.


