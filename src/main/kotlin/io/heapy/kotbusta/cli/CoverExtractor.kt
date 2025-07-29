package io.heapy.kotbusta.cli

import io.heapy.kotbusta.database.DatabaseInitializer
import io.heapy.kotbusta.parser.Fb2Parser
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.system.exitProcess

/**
 * Command-line utility for extracting book covers from FB2 archives
 * 
 * Usage: java -cp app.jar io.heapy.kotbusta.cli.CoverExtractorKt <books-data-path> [max-archives]
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: CoverExtractor <books-data-path> [max-archives]")
        println("Example: CoverExtractor /path/to/books 10")
        exitProcess(1)
    }
    
    val booksDataPath = Path(args[0]).toAbsolutePath()
    val maxArchives = args.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
    
    if (!booksDataPath.exists()) {
        println("❌ Books data path does not exist: $booksDataPath")
        exitProcess(1)
    }
    
    println("Kotbusta Cover Extractor")
    println("========================")
    println("Books data path: $booksDataPath")
    println("Max archives to process: ${if (maxArchives == Int.MAX_VALUE) "all" else maxArchives}")
    println()
    
    try {
        // Initialize database
        println("Initializing database...")
        DatabaseInitializer.initialize()
        println("Database initialized successfully")
        
        // Find FB2 archives
        val archives = booksDataPath.listDirectoryEntries("*.zip")
            .filter { it.fileName.toString().contains("fb2") }
            .take(maxArchives)
            .sorted()
        
        if (archives.isEmpty()) {
            println("❌ No FB2 archives found in $booksDataPath")
            exitProcess(1)
        }
        
        println("Found ${archives.size} FB2 archives to process")
        println()
        
        val parser = Fb2Parser()
        
        archives.forEachIndexed { index, archive ->
            println("Processing archive ${index + 1}/${archives.size}: ${archive.fileName}")
            try {
                parser.extractBookCovers(archive.toString())
                println("✅ Completed ${archive.fileName}")
            } catch (e: Exception) {
                println("❌ Error processing ${archive.fileName}: ${e.message}")
                e.printStackTrace()
            }
            println()
        }
        
        println("✅ Cover extraction completed!")
        println("Covers have been stored in the database and are accessible via /api/books/{id}/cover")
        
    } catch (e: Exception) {
        println()
        println("❌ Error during cover extraction: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}