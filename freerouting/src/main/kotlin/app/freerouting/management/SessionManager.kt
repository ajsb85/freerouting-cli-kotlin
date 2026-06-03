package app.freerouting.management

import app.freerouting.Freerouting.globalSettings
import app.freerouting.core.Session
import java.util.HashMap
import java.util.UUID

/*
 * This class is responsible for maintaining the list of active and past sessions.
 * If the user start the GUI, they will be assigned to a new session until they close the GUI.
 * API users will be assigned to a new session when they authenticate by providing their e-mail address.
 * One Freerouting process can have multiple active sessions at the same time.
 */
class SessionManager private constructor() {

    fun getSession(sessionId: String): Session? {
        return sessions[sessionId]
    }

    fun getSession(sessionId: String, userId: UUID): Session? {
        val session = getSession(sessionId) ?: return null

        if (session.userId != userId) {
            return null
        }

        return session
    }

    fun createSession(userId: UUID, host: String): Session {
        val session = Session(userId, host)
        sessions[session.id.toString()] = session
        globalSettings.statistics.incrementSessionsTotal()
        return session
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun getActiveSessionsCount(): Int {
        return sessions.size
    }

    fun listSessionIds(userId: UUID): Array<String> {
        return getSessions(null, userId).map { s -> s.id.toString() }.toTypedArray()
    }

    fun getGuiSession(): Session {
        for (session in sessions.values) {
            if (session.isGuiSession) {
                return session
            }
        }

        throw IllegalArgumentException("There is no GUI session.")
    }

    /**
     * Sets the session as a GUI session.
     *
     * @param sessionId
     * @throws IllegalArgumentException
     */
    fun setGuiSession(sessionId: UUID) {
        // Check if there are any other GUI sessions and if so, throw an exception because only one GUI session is allowed
        for (session in sessions.values) {
            if (session.isGuiSession) {
                throw IllegalArgumentException("There is already a GUI session.")
            }
        }

        val session = sessions[sessionId.toString()]
            ?: throw IllegalArgumentException("Session with id $sessionId does not exist.")
        
        session.isGuiSession = true

        if (!session.host.startsWith("Freerouting/")) {
            throw IllegalArgumentException(
                "Session with id $sessionId and host ${session.host} is not a valid GUI session. GUI sessions must have the prefix 'Freerouting/' for their host value."
            )
        }
    }

    fun getSessions(sessionId: String?, userId: UUID): Array<Session> {
        return if (sessionId == null) {
            sessions.values.filter { s -> s.userId == userId }.toTypedArray()
        } else {
            val session = sessions[sessionId]
            if (session != null) arrayOf(session) else emptyArray()
        }
    }

    companion object {
        @JvmField
        val instance = SessionManager()

        @JvmStatic
        fun getInstance(): SessionManager {
            return instance
        }

        private val sessions = HashMap<String, Session>()
    }
}
