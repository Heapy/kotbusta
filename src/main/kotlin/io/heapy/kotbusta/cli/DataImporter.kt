package io.heapy.kotbusta.cli

import io.heapy.kotbusta.database.DatabaseInitializer
import io.heapy.kotbusta.parser.InpxParser
import io.heapy.kotbusta.parser.Fb2Parser
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.system.exitProcess

/**
 * Command-line utility for importing INPX data into the database
 * 
 * Usage: java -cp app.jar io.heapy.kotbusta.cli.DataImporterKt <books-data-path> [--extract-covers] [max-cover-archives]
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: DataImporter <books-data-path> [--extract-covers] [max-cover-archives]")
        println("Example: DataImporter /path/to/books")
        println("Example: DataImporter /path/to/books --extract-covers 5")
        exitProcess(1)
    }
    
    val booksDataPath = Path(args[0]).toAbsolutePath()
    val extractCovers = args.contains("--extract-covers")
    val maxCoverArchives = args.find { it.toIntOrNull() != null }?.toInt() ?: 3

    println("Kotbusta Data Importer")
    println("=====================")
    println("Books data path: $booksDataPath")
    if (extractCovers) {
        println("Cover extraction: enabled (max $maxCoverArchives archives)")
    } else {
        println("Cover extraction: disabled (use --extract-covers to enable)")
    }
    println()
    
    try {
        // Initialize database
        println("Initializing database...")
        DatabaseInitializer.initialize()
        println("Database initialized successfully")
        
        // Parse and import INPX data
        println("Starting INPX import...")
        val parser = InpxParser()
        parser.parseAndImport(booksDataPath)
        
        // Extract covers if requested
        if (extractCovers) {
            println()
            println("Starting cover extraction...")
            
            val archives = booksDataPath.listDirectoryEntries("*.zip")
                .filter { it.fileName.toString().contains("fb2") }
                .take(maxCoverArchives)
                .sorted()
            
            if (archives.isNotEmpty()) {
                val fb2Parser = Fb2Parser()
                
                archives.forEachIndexed { index, archive ->
                    println("Extracting covers from archive ${index + 1}/${archives.size}: ${archive.fileName}")
                    try {
                        fb2Parser.extractBookCovers(archive.toString())
                        println("✅ Completed ${archive.fileName}")
                    } catch (e: Exception) {
                        println("❌ Error processing ${archive.fileName}: ${e.message}")
                    }
                }
                
                println("Cover extraction completed!")
            } else {
                println("No FB2 archives found for cover extraction")
            }
        }
        
        println()
        println("✅ Data import completed successfully!")
        if (!extractCovers) {
            println("💡 To extract book covers, run with --extract-covers flag")
        }
        println("You can now start the Kotbusta application.")
        
    } catch (e: Exception) {
        println()
        println("❌ Error during import: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}