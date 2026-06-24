package com.spencer.syncthing.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncthingApi {

    @GET("rest/system/version")
    suspend fun getVersion(): VersionResponse

    @GET("rest/system/status")
    suspend fun getSystemStatus(): SystemStatus

    @GET("rest/config/folders")
    suspend fun getFolders(): List<FolderConfig>

    @GET("rest/config/devices")
    suspend fun getDevices(): List<DeviceConfig>

    @GET("rest/db/status")
    suspend fun getFolderStatus(@Query("folder") folderId: String): FolderStatus

    @GET("rest/system/connections")
    suspend fun getConnections(): ConnectionsResponse

    @POST("rest/config/devices")
    suspend fun addDevice(@Body device: DeviceConfig)

    @POST("rest/config/folders")
    suspend fun addFolder(@Body folder: FolderConfig)

    @DELETE("rest/config/folders/{id}")
    suspend fun deleteFolder(@Path("id") folderId: String)

    @GET("rest/cluster/pending/devices")
    suspend fun getPendingDevices(): Map<String, PendingDeviceInfo>

    @GET("rest/cluster/pending/folders")
    suspend fun getPendingFolders(): Map<String, PendingFolderInfo>
}

data class VersionResponse(
    val version: String = "",
    val codename: String = "",
    val os: String = "",
    val arch: String = ""
)

data class SystemStatus(
    @SerializedName("myID") val myId: String = "",
    val startTime: String = "",
    val alloc: Long = 0,
    val goroutines: Int = 0
)

data class FolderConfig(
    val id: String = "",
    val label: String = "",
    val path: String = "",
    val type: String = "sendreceive",
    val devices: List<FolderDevice> = emptyList()
)

data class FolderDevice(
    val deviceID: String = ""
)

data class DeviceConfig(
    val deviceID: String = "",
    val name: String = "",
    val addresses: List<String> = emptyList()
)

data class FolderStatus(
    val state: String = "",
    val stateChanged: String = "",
    val globalBytes: Long = 0,
    val localBytes: Long = 0,
    val needBytes: Long = 0,
    val globalFiles: Int = 0,
    val needFiles: Int = 0,
    val inSyncFiles: Int = 0,
    val pullErrors: Int = 0
)

data class ConnectionsResponse(
    val connections: Map<String, ConnectionInfo> = emptyMap(),
    val total: ConnectionInfo = ConnectionInfo()
)

data class PendingDeviceInfo(
    val time: String = "",
    val name: String = "",
    val address: String = ""
)

data class PendingFolderInfo(
    val time: String = "",
    val offeredBy: Map<String, PendingFolderOfferedBy> = emptyMap()
)

data class PendingFolderOfferedBy(
    val time: String = "",
    val label: String = "",
    val receiveEncrypted: Boolean = false,
    val remoteEncrypted: Boolean = false
)

data class ConnectionInfo(
    val connected: Boolean = false,
    val inBytesTotal: Long = 0,
    val outBytesTotal: Long = 0,
    val address: String = ""
)
