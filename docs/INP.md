# The INP file format in Flibusta's digital library ecosystem

INP files in Flibusta are metadata catalog files that enable efficient organization and searching of massive ebook collections without accessing individual book files. These plain text files, packaged within INPX (ZIP) archives, contain structured records mapping numeric filenames to comprehensive book metadata including authors, titles, genres, and series information. The format, developed for the MyHomeLib library management system, has become the standard for Russian digital libraries, supporting collections exceeding 240GB with search times under 4 seconds across 20,000+ files.

## Understanding INP files in the Flibusta context

INP files serve as the backbone of Flibusta's cataloging system, functioning as lightweight database records that enable rapid searching and browsing of the library's vast collection. Unlike traditional library systems that might scan individual book files for metadata, INP files consolidate all essential information into efficiently parseable text records. Each INP file within an INPX archive corresponds to a specific ZIP file containing actual books, creating a two-tier storage system where metadata remains separate from content.

The INPX format itself is a **ZIP archive containing multiple components**: several .inp files (one per book archive), a structure.info file defining field layouts, and an archives.info file mapping INP files to their corresponding book archives. This architecture allows Flibusta to maintain a collection of hundreds of thousands of books while providing near-instantaneous search capabilities. A typical Flibusta distribution includes files named "flibusta_fb2_local.inpx" containing the complete catalog metadata.

The development history traces back to the early 2010s when Russian digital library communities needed efficient ways to manage growing collections. The format evolved through multiple iterations, with significant updates in 2010, 2011, and 2017, each adding capabilities for handling larger collections and supporting more metadata fields. Today, the format remains under active community development with regular updates to support modern library management needs.

## Technical structure and syntax specifications

Each record in an INP file follows a precise format with **13 fields separated by the ASCII character 0x04** rather than traditional delimiters. The standard field order is: AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;KEYWORDS, terminated by CR+LF (0x0D,0x0A). This unusual separator choice prevents conflicts with book metadata that might contain semicolons or commas.

The fields capture comprehensive book information: **AUTHOR** contains the author name(s), **GENRE** specifies the book's classification, **TITLE** holds the book title, **SERIES** and **SERNO** track series information and numbering, **FILE** provides the numeric filename reference, **SIZE** indicates file size in bytes, **LIBID** offers a unique library identifier, **DEL** flags deleted entries (0=active, 1=deleted), **EXT** specifies the file extension, **DATE** records addition date, **LANG** identifies the language code, and **KEYWORDS** stores additional searchable metadata.

Here's a practical Kotlin parsing example that demonstrates reading INP files:

```kotlin
import java.io.BufferedReader
import java.util.zip.ZipFile

const val FIELD_SEP = "\u0004"
const val RECORD_SEP = "\r\n"

ZipFile("flibusta_fb2_local.inpx").use { inpx ->
    val firstInp = inpx.entries().asSequence()
        .find { it.name.endsWith(".inp") }
        ?: throw Exception("No .inp file found")
    
    inpx.getInputStream(firstInp).bufferedReader().use { reader ->
        reader.lineSequence().forEach { line ->
            val fields = line.split(FIELD_SEP)
            val author = fields[0]
            val title = fields[2]
            val file = fields[5]
            // Process record...
        }
    }
}
```

Actual INP records look like this when parsed: "AUTHOR = Аббасзаде,Гусейн,:, TITLE = Белка, FILE = 24", where the FILE field "24" corresponds to a book file named "24.fb2" within the associated ZIP archive. The **UTF-8 encoding** ensures proper handling of Cyrillic and other international characters throughout the catalog.

## Integration within Flibusta's file format ecosystem

The INP format operates as the central nervous system connecting Flibusta's various components. While **FB2 (FictionBook 2.0) remains the primary ebook format**, with EPUB and PDF as alternatives, INP files provide the crucial metadata layer that makes the entire system searchable and manageable. The typical Flibusta library structure organizes books into numbered ZIP archives (fb2-001-1000.zip, fb2-1001-2000.zip, etc.) with the INPX file serving as the master index.

This architecture enables several sophisticated features: **incremental updates** allow users to download only new books rather than the entire collection, **genre filtering** permits selective importing of specific categories, and **author normalization** ensures consistent naming across the catalog. The separation of metadata from content also facilitates **bandwidth-efficient browsing** - users can search the entire collection using just the INPX file before downloading specific books.

The format integrates seamlessly with **OPDS (Open Publication Distribution System)** servers, enabling modern ereaders and mobile applications to browse Flibusta collections remotely. Web-based interfaces built on the INP format provide full-text search capabilities, faceted browsing, and even recommendation systems, all without requiring direct access to the book files themselves.

## Tools and software for working with INP files

The ecosystem around INP files includes numerous tools catering to different use cases. **MyHomeLib** remains the primary desktop application, offering comprehensive library management with advanced search, filtering, and export capabilities. The software handles INPX imports automatically, creating local SQLite databases for rapid searching. **FreeLib** provides a cross-platform alternative supporting Windows, Linux, and macOS, while **LightLib** offers web-based access without installation requirements.

For server deployments, **inpx-web** stands out as a modern Node.js solution providing both web interface and OPDS server functionality. It supports full-text search, remote library access, and can handle collections exceeding 20GB with minimal resource usage. The **Go-based inpxer** offers similar capabilities with Docker support for containerized deployments. Both tools transform static INPX files into dynamic, searchable web services.

Developers have access to several libraries and utilities: **InpxCreator** provides C++ tools for generating INPX files from database dumps, supporting both Flibusta and Librusec formats. The repository includes utilities like lib2inpx for format conversion and libget2 for automated library synchronization. For web developers, **COPS for MyHomeLib** offers PHP scripts to convert INPX data into web-friendly SQLite databases.

## Practical implementation and workflow

Setting up a Flibusta library using INP files follows a straightforward process. Users first download the library archives - multiple ZIP files containing books plus the INPX metadata file. After installing preferred management software, they import the INPX file, which creates a searchable database mapping numeric filenames to book information. The software then provides intuitive interfaces for browsing, searching, and extracting specific books from the archives.

**Performance optimization** becomes crucial for large collections. The INP format excels here - a 240GB library with over 20,000 books can be searched in approximately 4 seconds once indexed. The compressed INPX format typically reduces metadata storage by 70-80% compared to uncompressed alternatives. Modern implementations cache frequently accessed data and use database indexing on author, title, and genre fields for near-instantaneous responses.

For automated library management, tools like libget2 enable **scheduled synchronization** with Flibusta servers, downloading new books and updating the INPX catalog automatically. This approach maintains up-to-date collections without manual intervention, particularly valuable for mirror sites and distributed libraries.

## Conclusion

The INP format represents an elegant solution to a complex problem - how to efficiently catalog and search massive digital libraries while minimizing bandwidth and storage requirements. Its continued evolution and strong tool support demonstrate the format's effectiveness in real-world deployments serving millions of users. For anyone working with Flibusta or similar Russian digital libraries, understanding INP files unlocks powerful capabilities for library management, from simple desktop browsing to sophisticated web-based services.

The format's technical simplicity - essentially structured text records with careful delimiter choices - belies its sophistication in enabling complex library ecosystems. As digital libraries continue growing, the INP format's principles of separating metadata from content, supporting incremental updates, and maintaining human-readable structures provide valuable lessons for information organization at scale. Whether building a personal ebook server or contributing to community libraries, the INP format offers a proven foundation for efficient digital library management.
