package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface OrbitRepository {
    fun getAllProjects(): Flow<List<Project>>
    suspend fun insertProject(project: Project)

    fun getAllSessions(): Flow<List<ChatSession>>
    fun getSessionsForProject(projectId: String): Flow<List<ChatSession>>
    suspend fun insertSession(session: ChatSession)

    fun getMessagesForSession(sessionId: String): Flow<List<Message>>
    suspend fun insertMessage(message: Message)

    fun getAllAgents(): Flow<List<Agent>>
    suspend fun insertAgent(agent: Agent)

    fun getAllTermuxLogs(): Flow<List<TermuxLog>>
    suspend fun insertTermuxLog(log: TermuxLog)
}
