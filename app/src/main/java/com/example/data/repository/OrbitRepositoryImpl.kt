package com.example.data.repository

import com.example.data.local.dao.OrbitDao
import com.example.data.local.entity.*
import com.example.domain.model.*
import com.example.domain.repository.OrbitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OrbitRepositoryImpl(
    private val dao: OrbitDao
) : OrbitRepository {

    override fun getAllProjects(): Flow<List<Project>> =
        dao.getAllProjects().map { list -> list.map { it.toProject() } }

    override suspend fun insertProject(project: Project) {
        dao.insertProject(project.toEntity())
    }

    override fun getAllSessions(): Flow<List<ChatSession>> =
        dao.getAllSessions().map { list -> list.map { it.toSession() } }

    override fun getSessionsForProject(projectId: String): Flow<List<ChatSession>> =
        dao.getSessionsForProject(projectId).map { list -> list.map { it.toSession() } }

    override suspend fun insertSession(session: ChatSession) {
        dao.insertSession(session.toEntity())
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<Message>> =
        dao.getMessagesForSession(sessionId).map { list -> list.map { it.toMessage() } }

    override suspend fun insertMessage(message: Message) {
        dao.insertMessage(message.toEntity())
    }

    override fun getAllAgents(): Flow<List<Agent>> =
        dao.getAllAgents().map { list -> list.map { it.toAgent() } }

    override suspend fun insertAgent(agent: Agent) {
        dao.insertAgent(agent.toEntity())
    }

    override fun getAllTermuxLogs(): Flow<List<TermuxLog>> =
        dao.getAllTermuxLogs().map { list -> list.map { it.toTermuxLog() } }

    override suspend fun insertTermuxLog(log: TermuxLog) {
        dao.insertTermuxLog(log.toEntity())
    }
}
