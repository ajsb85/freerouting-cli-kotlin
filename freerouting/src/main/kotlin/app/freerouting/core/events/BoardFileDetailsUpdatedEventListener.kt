package app.freerouting.core.events

fun interface BoardFileDetailsUpdatedEventListener {
    fun onBoardFileDetailsUpdated(event: BoardFileDetailsUpdatedEvent)
}
