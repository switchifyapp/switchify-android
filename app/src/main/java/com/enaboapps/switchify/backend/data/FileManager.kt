package com.enaboapps.switchify.backend.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FileManager provides a standardized way to handle file operations in the app.
 * Uses device protected storage for secure file operations that can be accessed before device unlock.
 * Requires Android N (API 24) or above.
 *
 * Features:
 * - File CRUD operations using device protected storage
 * - Directory management
 * - JSON serialization/deserialization
 * - Coroutine support for IO operations
 * - Error handling and logging
 */
class FileManager private constructor(context: Context) {
    private val tag = "FileManager"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val protectedContext: Context = context.applicationContext
        .createDeviceProtectedStorageContext()
        ?: throw IllegalStateException("Failed to create device protected storage context")

    companion object {
        fun create(context: Context): FileManager = FileManager(context)
    }

    /**
     * Creates a new file in device protected storage.
     *
     * @param fileName Name of the file to create
     * @param subDirectory Optional subdirectory path
     * @return Result containing the created File or an error
     */
    suspend fun createFile(fileName: String, subDirectory: String? = null): Result<File> = 
        withContext(Dispatchers.IO) {
            try {
                val directory = if (subDirectory != null) {
                    File(protectedContext.filesDir, subDirectory).also { 
                        if (!it.exists()) it.mkdirs() 
                    }
                } else {
                    protectedContext.filesDir
                }
                
                val file = File(directory, fileName)
                if (!file.exists() && file.createNewFile()) {
                    Log.d(tag, "Created file: ${file.absolutePath}")
                    Result.success(file)
                } else {
                    Result.failure(IOException("Failed to create file: $fileName"))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error creating file: $fileName", e)
                Result.failure(e)
            }
        }

    /**
     * Reads the contents of a file from device protected storage.
     * The file is opened only for the duration of the read operation.
     */
    suspend fun readFile(fileName: String, subDirectory: String? = null): Result<String> = 
        withContext(Dispatchers.IO) {
            try {
                val file = getFile(fileName, subDirectory)
                if (!file.exists()) {
                    return@withContext Result.failure(IOException("File does not exist: $fileName"))
                }
                
                file.bufferedReader().use { reader ->
                    Result.success(reader.readText())
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reading file: $fileName", e)
                Result.failure(e)
            }
        }

    /**
     * Writes text content to a file in device protected storage.
     * The file is opened only for the duration of the write operation.
     */
    suspend fun writeFile(
        fileName: String, 
        content: String, 
        subDirectory: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = getFile(fileName, subDirectory)
            file.bufferedWriter().use { writer ->
                writer.write(content)
            }
            Log.d(tag, "Successfully wrote to file: ${file.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error writing to file: $fileName", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a file from device protected storage.
     *
     * @param fileName Name of the file to delete
     * @param subDirectory Optional subdirectory path
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(fileName: String, subDirectory: String? = null): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val file = getFile(fileName, subDirectory)
                if (file.exists() && file.delete()) {
                    Log.d(tag, "Successfully deleted file: ${file.absolutePath}")
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to delete file: $fileName"))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error deleting file: $fileName", e)
                Result.failure(e)
            }
        }

    /**
     * Writes an object to a file as JSON in device protected storage.
     * The file is opened only for the duration of the write operation.
     */
    suspend fun <T> writeJson(
        fileName: String, 
        data: T, 
        subDirectory: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(data)
            writeFile(fileName, json, subDirectory)
        } catch (e: Exception) {
            Log.e(tag, "Error writing JSON to file: $fileName", e)
            Result.failure(e)
        }
    }

    /**
     * Reads and deserializes JSON from a file in device protected storage.
     * The file is opened only for the duration of the read operation.
     */
    suspend fun <T> readJson(
        fileName: String, 
        type: Type,
        subDirectory: String? = null
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val result = readFile(fileName, subDirectory)
            if (result.isSuccess) {
                val json = result.getOrNull()!!
                Result.success(gson.fromJson(json, type))
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading JSON from file: $fileName", e)
            Result.failure(e)
        }
    }

    /**
     * Reads and deserializes JSON from a file in device protected storage.
     *
     * @param fileName Name of the file to read from
     * @param classOfT Class of the object to deserialize to
     * @param subDirectory Optional subdirectory path
     * @return Result containing the deserialized object or an error
     */
    suspend fun <T> readJson(
        fileName: String, 
        classOfT: Class<T>, 
        subDirectory: String? = null
    ): Result<T> = readJson(fileName, classOfT as Type, subDirectory)

    /**
     * Lists all files in a directory in device protected storage.
     *
     * @param subDirectory Optional subdirectory path
     * @return Result containing list of files or an error
     */
    suspend fun listFiles(subDirectory: String? = null): Result<List<File>> = 
        withContext(Dispatchers.IO) {
            try {
                val directory = if (subDirectory != null) {
                    File(protectedContext.filesDir, subDirectory)
                } else {
                    protectedContext.filesDir
                }

                if (directory.exists() && directory.isDirectory) {
                    val files = directory.listFiles()?.toList() ?: emptyList()
                    Result.success(files)
                } else {
                    Result.failure(IOException("Directory does not exist: $subDirectory"))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error listing files in directory: $subDirectory", e)
                Result.failure(e)
            }
        }

    /**
     * Gets a File object for the given file name and subdirectory in device protected storage.
     *
     * @param fileName Name of the file
     * @param subDirectory Optional subdirectory path
     * @return File object
     */
    private fun getFile(fileName: String, subDirectory: String? = null): File {
        val directory = if (subDirectory != null) {
            File(protectedContext.filesDir, subDirectory).also { 
                if (!it.exists()) it.mkdirs() 
            }
        } else {
            protectedContext.filesDir
        }
        return File(directory, fileName)
    }

    /**
     * Migrates files from regular storage to device protected storage.
     *
     * @param context The application context
     * @param subDirectory Optional subdirectory to migrate
     * @return Result indicating success or failure
     */
    suspend fun migrateFromRegularStorage(
        context: Context,
        subDirectory: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val regularDir = if (subDirectory != null) {
                File(context.applicationContext.filesDir, subDirectory)
            } else {
                context.applicationContext.filesDir
            }

            val protectedDir = if (subDirectory != null) {
                File(protectedContext.filesDir, subDirectory)
            } else {
                protectedContext.filesDir
            }

            if (!regularDir.exists() || !regularDir.isDirectory) {
                return@withContext Result.success(Unit)
            }

            regularDir.listFiles()?.forEach { file ->
                val targetFile = File(protectedDir, file.name)
                if (!targetFile.exists()) {
                    file.copyTo(targetFile, overwrite = true)
                }
                file.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error migrating to protected storage", e)
            Result.failure(e)
        }
    }
} 