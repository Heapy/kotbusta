#!/bin/bash

# Unpack flibusta-sample directory from password-protected archive

PASSWORD="Сорок тысяч обезьян в жопу сунули банан"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$SCRIPT_DIR" || exit 1

if [ ! -f "flibusta-sample.zip" ]; then
    echo "Error: flibusta-sample.zip not found"
    exit 1
fi

echo "Unpacking flibusta-sample.zip..."

unzip -P "$PASSWORD" flibusta-sample.zip

echo "✓ Unpacked flibusta-sample directory"
