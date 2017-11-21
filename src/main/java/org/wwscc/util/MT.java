/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.wwscc.util;

/**
 *
 * @author bwilson
 */
public enum MT {

    // Generic ?
    SERIES_CHANGED,
    EVENT_CHANGED,
    COURSE_CHANGED,
    RUNGROUP_CHANGED,
    SCANNER_OPTIONS_CHANGED,
    BARCODE_SCANNED,
    CAR_CREATED,
    QUICKID_SEARCH,

    // Database events
    DATABASE_NOTIFICATION,

    // TrayApplication
    POKE_SYNC_SERVER,
    DISCOVERY_CHANGE,
    USING_MACHINE,
    MACHINE_STATUS,
    BACKEND_STATUS,
    BACKEND_READY,
    IMPORT_REQUEST,
    DEBUG_REQUEST,
    LAUNCH_REQUEST,
    OPEN_STATUS_REQUEST,
    SHUTDOWN_REQUEST,


    // Admin
    //DELETE_SELECTED,

    // Data Entry
    OBJECT_CLICKED,
    OBJECT_DCLICKED,
    ENTRANTS_CHANGED,
    //NEXT_TO_FINISH,
    RUN_CHANGED,
    TIME_ENTER_REQUEST,
    TIME_ENTERED,
    TIME_RECEIVED,
    CAR_ADD,
    CAR_CHANGE,
    TIMER_TAKES_FOCUS,
    FILTER_ENTRANT,
    OPEN_BARCODE_ENTRY,
    OPEN_FILTER,
    SHOW_ADD_PANE,

    // Timer Net Service
    TIMER_SERVICE_CONNECTION,
    TIMER_SERVICE_DIALIN,
    TIMER_SERVICE_RUN,
    TIMER_SERVICE_DELETE,
    TIMER_SERVICE_LISTENING,
    TIMER_SERVICE_NOTLISTENING,

    // Serial Timer Input
    SERIAL_TIMER_DATA,
    SERIAL_TIMESTAMP,
    SERIAL_GENERIC_DATA,
    SERIAL_PORT_OPEN,
    SERIAL_PORT_CLOSED,

    // Challenge GUI
    NEW_CHALLENGE,
    CHALLENGE_CHANGED,
    CHALLENGE_EDIT_REQUEST,
    CHALLENGE_DELETED,
    MODEL_CHANGED,
    ACTIVE_CHANGE_REQUEST,
    ACTIVE_CHANGE,
    ENTRANT_CHANGED,
    AUTO_WIN,
    PRINT_BRACKET,
    CONNECT_REQUEST,
    PRELOAD_MENU,


    // ProTimer
    TREE,
    DIALIN_LEFT,
    DIALIN_RIGHT,
    OPEN_SENSOR,
    DELETE_START_LEFT,
    DELETE_START_RIGHT,
    DELETE_FINISH_LEFT,
    DELETE_FINISH_RIGHT,
    ALIGN_MODE,
    RUN_MODE,
    CONTROL_DATA,

    REACTION_LEFT,
    REACTION_RIGHT,
    SIXTY_LEFT,
    SIXTY_RIGHT,
    FINISH_LEFT,
    FINISH_RIGHT,
    WIN_LEFT,
    WIN_RIGHT,
    LEAD_LEFT,
    LEAD_RIGHT,
    CHALDIAL_LEFT,
    CHALDIAL_RIGHT,
    CHALWIN_LEFT,
    CHALWIN_RIGHT,

    INPUT_START_LEFT,
    INPUT_START_RIGHT,
    INPUT_FINISH_LEFT,
    INPUT_FINISH_RIGHT,
    INPUT_DELETE_START_LEFT,
    INPUT_DELETE_START_RIGHT,
    INPUT_DELETE_FINISH_LEFT,
    INPUT_DELETE_FINISH_RIGHT,

    INPUT_SET_DIALIN,
    INPUT_SET_RUNMODE,
    INPUT_SET_ALIGNMODE,
    INPUT_SHOW_INPROGRESS,
    INPUT_SHOW_STATE,
    INPUT_RESET_SOFT,
    INPUT_RESET_HARD,
    INPUT_TEXT,
    SENDING_SERIAL
}
