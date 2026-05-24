package com.example.data.dao

import androidx.room.*
import com.example.data.model.Clip
import com.example.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY viralScore DESC")
    fun getClipsForProject(projectId: Int): Flow<List<Clip>>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Int): Clip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: Clip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<Clip>)

    @Update
    suspend fun updateClip(clip: Clip)

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: Int)

    @Query("SELECT * FROM clips WHERE isExported = 1 ORDER BY id DESC")
    fun getExportedClips(): Flow<List<Clip>>
}
