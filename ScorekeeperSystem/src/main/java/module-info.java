module org.wwscc.system {
    exports org.wwscc.system;

    requires org.wwscc.storage;
    requires org.wwscc.util;

    requires com.fasterxml.jackson.databind;

    requires java.desktop;
    requires java.logging;
    requires java.sql;

    /*
    requires transitive org.wwscc.util;
    requires java.logging;
    requires java.sql;
    //requires jbcrypt;
    requires postgresql;
    */
}
