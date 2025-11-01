#!/bin/bash

# Pack flibusta-sample directory with password protection

PASSWORD="Сорок тысяч обезьян в жопу сунули банан"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$SCRIPT_DIR" || exit 1

echo "Packing flibusta-sample directory..."

zip -r -P "$PASSWORD" flibusta-sample.zip flibusta-sample/

echo "✓ Created flibusta-sample.zip"
