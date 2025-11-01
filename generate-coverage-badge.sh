#!/bin/bash

set -e

# Run tests and generate JaCoCo report
echo "Running tests and generating coverage report..."
./gradlew test jacocoTestReport

# Create badges directory if it doesn't exist
mkdir -p .github/badges

# Extract coverage from JaCoCo XML report
COVERAGE=$(python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('build/reports/jacoco/test/jacocoTestReport.xml')
root = tree.getroot()

missed = 0
covered = 0

for counter in root.findall('.//counter[@type=\"INSTRUCTION\"]'):
    missed += int(counter.get('missed', 0))
    covered += int(counter.get('covered', 0))

total = missed + covered
if total > 0:
    percentage = (covered / total) * 100
    print(f'{percentage:.1f}')
else:
    print('0')
")

echo "Coverage: ${COVERAGE}%"

# Determine badge color
if (( $(echo "$COVERAGE >= 80" | bc -l) )); then
  COLOR="brightgreen"
elif (( $(echo "$COVERAGE >= 60" | bc -l) )); then
  COLOR="yellow"
elif (( $(echo "$COVERAGE >= 40" | bc -l) )); then
  COLOR="orange"
else
  COLOR="red"
fi

# Download badge from shields.io
echo "Generating badge..."
curl -s "https://img.shields.io/badge/coverage-${COVERAGE}%25-${COLOR}" > .github/badges/jacoco.svg

echo "✓ Badge generated at .github/badges/jacoco.svg"
