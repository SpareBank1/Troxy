package no.sb1.troxy.common;

/**
 * The simulator mode, denoting whether to playback, record, etc.

 */
public enum Mode {
    /**
     * Playback recordings, will fail if there are no matching recordings.
     */
    PLAYBACK,
    /**
     * Record request/responses, even if a matching recording already exists.
     */
    RECORD,
    /**
     * Playback if a matching recording exists, if not then record the request/responses.
     */
    PLAYBACK_OR_RECORD,
    /**
     * Neither playback nor record, let the call pass through.
     */
    PASSTHROUGH,
    /**
     * Playback if a matching recording exists, otherwise let the call pass through.
     */
    PLAYBACK_OR_PASSTHROUGH
}
