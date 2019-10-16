module org.wwscc.util {
    exports org.wwscc.util;

    requires transitive java.desktop;
    requires java.logging;
    requires java.prefs;
    requires javafx.graphics;
    requires javafx.controls;
    requires transitive com.fasterxml.jackson.databind;
    requires javafx.web;
    requires com.fazecast.jSerialComm;
}