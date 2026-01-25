package com.twopc.common.protocol;

// protocol flow
// coordinator -> participants: PREPARE
// participants -> Coordinator: VOTE_YES OR VOTE_NO
// Coordinator -> Participants: COMMIT(if all yes) or ABORT(if any NO)
public enum MessageType {
    PREPARE,
    VOTE_YES,
    VOTE_NO,
    COMMIT,
    ABORT,
    QUERY_STATUS,
    STATUS_RESPONSE;
}
