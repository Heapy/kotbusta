package io.heapy.kotbusta.cli

import io.heapy.kotbusta.database.DatabaseInitializer
import io.heapy.kotbusta.parser.InpxParser
import kotlin.io.path.Path
import kotlin.system.exitProcess

/**
 * Command-line utility for importing INPX data into the database
 * 
 * Usage: java -cp app.jar io.heapy.kotbusta.cli.DataImporterKt <books-data-path>
 */
fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: DataImporter <books-data-path>")
        println("Example: DataImporter /path/to/books")
        exitProcess(1)
    }
    
    val booksDataPath = Path(args[0]).toAbsolutePath()

    println("Kotbusta Data Importer")
    println("=====================")
    println("Books data path: $booksDataPath")
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
        
        println()
        println("✅ Data import completed successfully!")
        println("You can now start the Kotbusta application.")
        
    } catch (e: Exception) {
        println()
        println("❌ Error during import: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}