#!/usr/bin/env python3

import argparse
import os
import shutil
import subprocess
import sys


BASH = """#!/usr/bin/env bash
cd $(dirname ${{BASH_SOURCE[0]}})/bin
./java -classpath {classpath} {mainclass}
"""

BATCH = """cd %~dp0bin
start javaw.exe -classpath {classpath} {mainclass}
"""

WINRULES = """%echo off

CALL :addrule ScorekeeperWeb       TCP 80
CALL :addrule ScorekeeperTimers    TCP 54328
CALL :addrule ScorekeeperDatabase  TCP 54329
CALL :addrule ScorekeeperDNS       UDP 53
CALL :addrule ScorekeeperMDNS      UDP 5353
CALL :addrule ScorekeeperDiscovery UDP 5454

CALL :disableservice w3svc
CALL :disableservice SharedAccess

pause
exit /B 0

:addrule
netsh advfirewall firewall show rule name=%1
if %ERRORLEVEL% NEQ 0  netsh advfirewall firewall add rule name=%1 dir=in action=allow protocol=%2 localport=%3
EXIT /B 0

:disableservice
sc query %1
if %ERRORLEVEL% EQU 0 sc stop %1 & sc config %1 start=disabled
EXIT /B 0
"""


class Dist():

    def __init__(self, args):
        print(args)
        self.__dict__.update(vars(args))
        self.builddir = "build/runtime/"+self.target
        self.pairs = [
            ('StartScorekeeper', 'org.wwscc.system.ScorekeeperSystem'),
            #('LoadCerts',        'org.wwscc.system.LoadCerts'),
            ('StartProTimer',    'org.wwscc.protimer.ProSoloInterface'),
        ]


    def basejre(self):
        if not os.path.exists(self.builddir):
            subprocess.run(["jlink", "-v", "--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages", 
                             "--module-path", self.jdk+"/jmods/", "--output", self.builddir, "--add-modules", self.modules], check=True)

    def copyapp(self):
        self.basejre()
        self.jars = list()

        libdir = os.path.join(self.app, "lib")
        for f in os.listdir(libdir):
            self.jars.append(f)
            shutil.copy(os.path.join(libdir,f), os.path.join(self.builddir, "lib"))
        

    def createdist(self):
        self.copyapp()
        if self.target == 'win':
            self.createScripts(BATCH, ".bat", ";")
            self.executableFile(os.path.join(self.builddir, "rules.bat"), WINRULES)
        else:
            self.createScripts()

        name = "build/runtime/Scorekeeper-{version}-{target}".format(**self.__dict__)
        shutil.make_archive(name, "zip", self.builddir)

    def createScripts(self, template=BASH, ext="", separator=":"):
        classpath = separator.join(map(lambda x: os.path.join("../lib",x), self.jars))
        for name, mainclass in self.pairs:
            self.executableFile(os.path.join(self.builddir, name+ext), template.format(classpath=classpath, mainclass=mainclass))

    def executableFile(self, path, data):
        with open(path, "w") as fp:
            fp.write(data)
        os.chmod(path, 0o777)



parser = argparse.ArgumentParser(description='Create distribution')
parser.add_argument('--modules', required=True)
parser.add_argument('--jdk',     required=True)
parser.add_argument('--app',     required=True)
parser.add_argument('--target',  required=True)
parser.add_argument('--version', required=True)
args = parser.parse_args()

Dist(args).createdist()
