
# Scorekeeper Frontend

[![Build Status](https://travis-ci.org/drytoastman/scorekeeperfrontend.svg?branch=master)](https://travis-ci.org/drytoastman/scorekeeperfrontend)
[![Known Vulnerabilities](https://snyk.io/test/github/drytoastman/scorekeeperfrontend/badge.svg)](https://snyk.io/test/github/drytoastman/scorekeeperfrontend)

User docs are generated from the docs/ folder and posted at https://drytoastman.github.io/scorekeeperfrontend/

Building the frontend makes use of gradle for dependency downloads, compilation and jaring.  The project also
includes project files for eclipse which makes development easier but still integrates with gradle.

The command `./gradlew` will build an uber-jar with all the necessary classes and data in one jar file in ./build/libs/

`org.wwscc.tray.TrayApplications` is the default class to start execution from.  TrayApplication sits in the system tray
and is repsonsible for starting and monitoring the backend docker containers (database, webserver, etc) and if using docker-machine
to run containers, it will open forwarding ports across the VirtualBox boundary.  It also provides the status windows
with backend and sync status, menus, etc.

