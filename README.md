# This repository is being sunsetted as we move GUI piece to Electron in https://github.com/drytoastman/scorekeeperts

# Scorekeeper Frontend

[![Build Status](https://travis-ci.org/drytoastman/scorekeeperfrontend.svg?branch=master)](https://travis-ci.org/drytoastman/scorekeeperfrontend)

User docs are generated from and for the backend.  See https://scorekeeper.wwscc.org/docs/

Building the frontend makes use of gradle for dependency downloads, compilation and jaring.  The project also
includes project files for eclipse which makes development easier but still integrates with gradle.

The command `./gradlew` will build an uber-jar with all the necessary classes and data in one jar file in ./build/libs/

`org.wwscc.system.ScorekeeperSystem` is the default class to start execution from.  ScorekeeperSystem is repsonsible for
starting and monitoring the backend docker containers (database, server, etc) It also provides the status windows
with backend and sync status, menus, etc.
