module org.wwscc.storage {
    exports org.wwscc.storage;

    requires transitive com.fasterxml.jackson.databind;
    requires transitive org.wwscc.util;
    requires java.logging;
    requires java.sql;
    //requires jbcrypt;
    requires postgresql;
}
