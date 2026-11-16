package com.ds.goroute.type;

/**
 * What kind of spot a check-in is about, said by its author when the spot is not in the
 * catalogue (V289). It puts such a post under the Feed's place-kind chips; a check-in with a
 * catalogue place takes the place's kind instead, which is why only a check-in without one
 * stores it. The SQL functions {@code goroute_checkin_kind_group} and
 * {@code goroute_checkin_kind_sub_types} map each value to the place group and sub types the
 * chips filter on.
 */
public enum CheckinPlaceKind {
    ATTRACTION,
    DRINK,
    RESTAURANT
}
