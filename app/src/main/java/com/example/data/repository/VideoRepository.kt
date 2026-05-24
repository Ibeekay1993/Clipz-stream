package com.example.data.repository

import com.example.data.dao.ProjectDao
import com.example.data.model.Clip
import com.example.data.model.Project
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val exportedClips: Flow<List<Clip>> = projectDao.getExportedClips()

    suspend fun getProjectById(id: Int): Project? = projectDao.getProjectById(id)

    suspend fun insertProject(project: Project): Int {
        return projectDao.insertProject(project).toInt()
    }

    suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(project)
    }

    fun getClipsForProject(projectId: Int): Flow<List<Clip>> = projectDao.getClipsForProject(projectId)

    suspend fun getClipById(id: Int): Clip? = projectDao.getClipById(id)

    suspend fun insertClip(clip: Clip): Int {
        return projectDao.insertClip(clip).toInt()
    }

    suspend fun insertClips(clips: List<Clip>) {
        projectDao.insertClips(clips)
    }

    suspend fun updateClip(clip: Clip) {
        projectDao.updateClip(clip)
    }

    suspend fun deleteClipById(clipId: Int) {
        projectDao.deleteClipById(clipId)
    }
}
