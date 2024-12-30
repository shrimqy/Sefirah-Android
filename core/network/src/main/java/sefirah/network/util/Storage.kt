package sefirah.network.util

import android.os.Environment
import android.os.StatFs
import sefirah.domain.model.DirectoryInfo
import sefirah.domain.model.StorageInfo
import java.io.File
import java.text.DecimalFormat

fun getStorageInfo(): StorageInfo {
    val externalStorage = Environment.getExternalStorageDirectory()
    val stat = StatFs(externalStorage.path)

    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong

    val totalSpace = totalBlocks * blockSize // Total storage space in bytes
    val availableSpace = availableBlocks * blockSize // Available storage space in bytes
    val usedSpace = totalSpace - availableSpace // Used storage space in bytes

    // Return the formatted storage info as a StorageInfo object
    return StorageInfo(
        totalSpace = totalSpace,
        freeSpace = availableSpace,
        usedSpace = usedSpace
    )
}

fun formatSize(size: Long): String {
    val kilo = 1024.0
    val mega = kilo * 1024
    val giga = mega * 1024

    val formatter = DecimalFormat("#.##")

    return when {
        size >= giga -> {formatter.format(size / giga)}
        size >= mega -> {formatter.format(size / mega)}
        else -> {formatter.format(size / kilo)}
    }
}

fun getRootDirectory(): List<DirectoryInfo> {
    val rootDirectory = Environment.getExternalStorageDirectory()
    return listFilesInDirectory(rootDirectory)
}


// Function to retrieve files and folders in a given directory
fun listFilesInDirectory(directory: File): List<DirectoryInfo> {
    val fileList = mutableListOf<DirectoryInfo>()

    // Get list of files and folders in the directory
    val files = directory.listFiles() ?: return emptyList()

    for (file in files) {
        fileList.add(
            DirectoryInfo(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) formatSize(file.length()) else formatSize(file.totalSpace)  // Get size only for files
            )
        )
    }
    return fileList
}

fun getDirectoryStructure(path: String): List<DirectoryInfo> {
    val directory = File(path)
    if (directory.exists() && directory.isDirectory) {
        return listFilesInDirectory(directory)
    }
    return emptyList() // Return an empty list if the directory doesn't exist
}