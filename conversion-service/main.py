#!/usr/bin/env python3

import os
import tempfile
import subprocess
import zipfile
from pathlib import Path
from typing import Optional
import asyncio
import aiofiles

from fastapi import FastAPI, HTTPException, UploadFile, File, BackgroundTasks
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="FB2 Conversion Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure properly in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Supported formats
SUPPORTED_FORMATS = ["epub", "mobi", "pdf", "txt"]

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy", "service": "fb2-conversion"}

@app.post("/convert/{target_format}")
async def convert_fb2(
    target_format: str,
    background_tasks: BackgroundTasks,
    fb2_file: UploadFile = File(...),
    book_title: Optional[str] = None
):
    """
    Convert FB2 file to specified format using Calibre
    """
    if target_format not in SUPPORTED_FORMATS:
        raise HTTPException(
            status_code=400, 
            detail=f"Unsupported format. Supported formats: {', '.join(SUPPORTED_FORMATS)}"
        )
    
    if not fb2_file.filename.endswith('.fb2'):
        raise HTTPException(status_code=400, detail="File must be in FB2 format")
    
    try:
        # Create temporary directory for processing
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            
            # Save uploaded FB2 file
            fb2_path = temp_path / "input.fb2"
            async with aiofiles.open(fb2_path, 'wb') as f:
                content = await fb2_file.read()
                await f.write(content)
            
            # Generate output filename
            base_name = book_title or fb2_file.filename.replace('.fb2', '')
            output_filename = f"{base_name}.{target_format}"
            output_path = temp_path / output_filename
            
            # Run Calibre conversion
            success = await convert_with_calibre(fb2_path, output_path, target_format)
            
            if not success or not output_path.exists():
                raise HTTPException(status_code=500, detail="Conversion failed")
            
            # Schedule cleanup
            background_tasks.add_task(cleanup_file, output_path)
            
            # Return converted file
            return FileResponse(
                path=str(output_path),
                filename=output_filename,
                media_type=get_media_type(target_format)
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Conversion error: {str(e)}")

@app.post("/convert-from-archive/{target_format}")
async def convert_from_archive(
    target_format: str,
    background_tasks: BackgroundTasks,
    archive_file: UploadFile = File(...),
    fb2_filename: str = "",
    book_title: Optional[str] = None
):
    """
    Extract FB2 from archive and convert to specified format
    """
    if target_format not in SUPPORTED_FORMATS:
        raise HTTPException(
            status_code=400, 
            detail=f"Unsupported format. Supported formats: {', '.join(SUPPORTED_FORMATS)}"
        )
    
    try:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            
            # Save uploaded archive
            archive_path = temp_path / "archive.zip"
            async with aiofiles.open(archive_path, 'wb') as f:
                content = await archive_file.read()
                await f.write(content)
            
            # Extract FB2 file from archive
            fb2_path = temp_path / "input.fb2"
            if not await extract_fb2_from_archive(archive_path, fb2_filename, fb2_path):
                raise HTTPException(status_code=404, detail="FB2 file not found in archive")
            
            # Generate output filename
            base_name = book_title or fb2_filename.replace('.fb2', '')
            output_filename = f"{base_name}.{target_format}"
            output_path = temp_path / output_filename
            
            # Run Calibre conversion
            success = await convert_with_calibre(fb2_path, output_path, target_format)
            
            if not success or not output_path.exists():
                raise HTTPException(status_code=500, detail="Conversion failed")
            
            # Schedule cleanup
            background_tasks.add_task(cleanup_file, output_path)
            
            # Return converted file
            return FileResponse(
                path=str(output_path),
                filename=output_filename,
                media_type=get_media_type(target_format)
            )
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Conversion error: {str(e)}")

async def convert_with_calibre(input_path: Path, output_path: Path, target_format: str) -> bool:
    """
    Convert file using Calibre's ebook-convert command
    """
    try:
        cmd = [
            "ebook-convert",
            str(input_path),
            str(output_path),
            "--output-profile", "generic_eink" if target_format in ["epub", "mobi"] else "default",
        ]
        
        # Add format-specific options
        if target_format == "epub":
            cmd.extend([
                "--epub-version", "2",
                "--flow-size", "260",
                "--no-default-epub-cover"
            ])
        elif target_format == "mobi":
            cmd.extend([
                "--mobi-file-type", "both",
                "--mobi-toc-at-start"
            ])
        elif target_format == "pdf":
            cmd.extend([
                "--pdf-page-numbers",
                "--paper-size", "a4",
                "--pdf-default-font-size", "12"
            ])
        
        # Run conversion
        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        
        stdout, stderr = await process.communicate()
        
        if process.returncode == 0:
            return True
        else:
            print(f"Calibre conversion failed: {stderr.decode()}")
            return False
            
    except Exception as e:
        print(f"Calibre conversion error: {str(e)}")
        return False

async def extract_fb2_from_archive(archive_path: Path, fb2_filename: str, output_path: Path) -> bool:
    """
    Extract specified FB2 file from ZIP archive
    """
    try:
        with zipfile.ZipFile(archive_path, 'r') as zip_ref:
            # If no specific filename provided, find first FB2 file
            if not fb2_filename:
                fb2_files = [name for name in zip_ref.namelist() if name.endswith('.fb2')]
                if not fb2_files:
                    return False
                fb2_filename = fb2_files[0]
            
            # Extract the FB2 file
            if fb2_filename in zip_ref.namelist():
                with zip_ref.open(fb2_filename) as source:
                    async with aiofiles.open(output_path, 'wb') as target:
                        content = source.read()
                        await target.write(content)
                return True
            else:
                return False
                
    except Exception as e:
        print(f"Archive extraction error: {str(e)}")
        return False

def get_media_type(format: str) -> str:
    """
    Get appropriate media type for format
    """
    media_types = {
        "epub": "application/epub+zip",
        "mobi": "application/x-mobipocket-ebook",
        "pdf": "application/pdf",
        "txt": "text/plain"
    }
    return media_types.get(format, "application/octet-stream")

async def cleanup_file(file_path: Path):
    """
    Background task to clean up temporary files
    """
    try:
        if file_path.exists():
            os.unlink(file_path)
    except Exception as e:
        print(f"Cleanup error: {str(e)}")

@app.get("/formats")
async def get_supported_formats():
    """
    Get list of supported output formats
    """
    return {"formats": SUPPORTED_FORMATS}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)