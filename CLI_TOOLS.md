# Kotbusta CLI Tools

This document describes the command-line tools available for managing your Kotbusta digital library.

## DataImporter

Imports book metadata from Flibusta INPX files into the database.

### Usage

```bash
# Basic import (metadata only)
./gradlew run --args="io.heapy.kotbusta.cli.DataImporterKt /path/to/books"

# Import with cover extraction (from first 3 archives)
./gradlew run --args="io.heapy.kotbusta.cli.DataImporterKt /path/to/books --extract-covers"

# Import with cover extraction (from first 10 archives)
./gradlew run --args="io.heapy.kotbusta.cli.DataImporterKt /path/to/books --extract-covers 10"
```

### What it does

1. **Initializes the database** - Creates all necessary tables
2. **Parses INPX metadata** - Reads `flibusta_fb2_local.inpx` file
3. **Imports book records** - Adds books, authors, series to database
4. **Extracts covers** (optional) - Reads FB2 files and extracts cover images

### Requirements

- `flibusta_fb2_local.inpx` file in the books directory
- FB2 archive files (*.zip) in the books directory
- Sufficient disk space for database and extracted covers

## CoverExtractor

Standalone tool for extracting book covers from FB2 archives.

### Usage

```bash
# Extract covers from all archives
./gradlew run --args="io.heapy.kotbusta.cli.CoverExtractorKt /path/to/books"

# Extract covers from first 5 archives only
./gradlew run --args="io.heapy.kotbusta.cli.CoverExtractorKt /path/to/books 5"
```

### What it does

1. **Finds FB2 archives** - Locates *.zip files containing FB2 books
2. **Extracts cover images** - Reads embedded images from FB2 files
3. **Stores in database** - Saves cover images as BLOB data
4. **Makes covers available** - Covers accessible via `/api/books/{id}/cover`

### Performance Notes

- Cover extraction is **memory intensive** - processes one archive at a time
- **Large archives** (>1GB) may take several minutes to process
- **Recommended approach**: Start with a small number of archives (5-10)

## Directory Structure

Your books directory should look like this:

```
/path/to/books/
├── flibusta_fb2_local.inpx          # Metadata index (required)
├── fb2-000024-030559.zip            # FB2 books archive
├── fb2-030560-060423.zip            # FB2 books archive  
├── fb2-168103-172702.zip            # FB2 books archive
├── f.fb2-173909-177717.zip          # FB2 books archive
└── ... (more archives)
```

## Tips and Best Practices

### Initial Setup

1. **Start with metadata only**:
   ```bash
   ./gradlew run --args="io.heapy.kotbusta.cli.DataImporterKt /path/to/books"
   ```

2. **Test with a few covers**:
   ```bash
   ./gradlew run --args="io.heapy.kotbusta.cli.CoverExtractorKt /path/to/books 3"
   ```

3. **Extract more covers gradually**:
   ```bash
   ./gradlew run --args="io.heapy.kotbusta.cli.CoverExtractorKt /path/to/books 10"
   ```

### Performance Optimization

- **Process covers in batches** - Don't try to extract all covers at once
- **Monitor disk space** - Cover images can use significant storage
- **Run during off-peak hours** - Cover extraction is CPU/memory intensive
- **Consider SSD storage** - Faster I/O improves processing speed

### Troubleshooting

**"No books imported"**
- Check that `flibusta_fb2_local.inpx` exists in the books directory
- Verify the INPX file is not corrupted (should be ~35MB)

**"Cover extraction failed"**
- Ensure FB2 archive files are not corrupted
- Check available memory (each archive may use 100-500MB RAM)
- Try processing fewer archives at once

**"Database locked"**
- Stop the Kotbusta application before running CLI tools
- Only run one CLI tool at a time

### Production Deployment

For production deployments using Docker:

```bash
# Run data import in container
docker-compose exec kotbusta-app java -cp app.jar \
  io.heapy.kotbusta.cli.DataImporterKt /app/books-data --extract-covers 5

# Run cover extraction separately
docker-compose exec kotbusta-app java -cp app.jar \
  io.heapy.kotbusta.cli.CoverExtractorKt /app/books-data 10
```

## Database Information

After successful import, your database will contain:

- **Books table**: ~800,000+ book records (typical Flibusta collection)
- **Authors table**: ~100,000+ author records  
- **Series table**: ~50,000+ book series
- **Cover images**: Varies based on extraction settings

The database file (`data/kotbusta.db`) will typically be:
- **Without covers**: ~200-300MB
- **With covers**: 1-5GB+ (depending on how many extracted)