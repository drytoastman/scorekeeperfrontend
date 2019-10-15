module scorekeeper {
    // auto generated, should these be one?
    exports org.wwscc.system;
    exports org.wwscc.storage;
    exports org.wwscc.system.docker;
    exports org.wwscc.system.docker.models;
    opens org.wwscc.system.docker.models;
    /*
    exports org.wwscc.protimer;
    exports org.wwscc.util;
    exports org.wwscc.dataentry.tables;
    exports org.wwscc.components;
    exports org.wwscc.dialogs;
    exports org.wwscc.barcodes;
    exports org.wwscc.timercomm;
    exports org.wwscc.dataentry.actions;
    exports org.wwscc.registration;
    exports org.wwscc.actions;
    exports org.wwscc.fxchallenge;
    exports org.wwscc.challenge;
    exports org.wwscc.challenge.viewer;
    exports org.wwscc.dataentry;
    exports org.wwscc.system.docker.models;
    */

    requires java.annotation;
    requires transitive java.desktop;
    requires transitive java.sql;
    requires java.prefs;
    requires jdk.jsobject;

    requires javafx.base;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires javafx.swing;
    requires javafx.web;

    requires com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;

    //requires transitive httpcore;
    requires miglayout.swing;
    requires swagger.annotations;


    /*
    requires com.fasterxml.jackson.core;
    //requires httpclient;
    //requires transitive httpcore;
    //requires java.annotation;
    requires java.datatransfer;
    requires java.logging;
    requires java.xml;


    //requires jbcrypt;
    //requires jnr.unixsocket;
    //requires jsch;
    //requires jtar;
    requires nrjavaserial;
    //requires org.apache.commons.io;
    requires postgresql;
    */
}