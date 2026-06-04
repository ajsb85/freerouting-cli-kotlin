package app.freerouting.core.events

import app.freerouting.core.BoardFileDetails
import java.util.EventObject

class BoardFileDetailsUpdatedEvent(
    source: Any,
    @JvmField val details: BoardFileDetails
) : EventObject(source) {

    fun getDetails(): BoardFileDetails = details
}
