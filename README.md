
# Scorekeeper Frontend

Building the frontend makes use of gradle for dependency downloads, compilation and jaring.  The project also
includes project files for eclipse which makes development easier but still integrates with gradle.

The command `./gradlew` will build an uber-jar with all the necessary classes and data in one jar file in ./build/libs/

`org.wwscc.util.Launcher` is still the default class to start execute from.  Without any arguments it defaults
to the `org.wwscc.tray.TrayMonitor` application.  TrayMonitor sits in the system tray and is repsonsible for
starting and monitoring the backend docker containers (database, webserver, etc) and if using docker-machine
to run containers, it will open forwarding ports across the VirtualBox boundary.

