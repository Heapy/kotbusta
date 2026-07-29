#!/bin/sh

set -eu

PROJECT_ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
REPORT_PATH="$PROJECT_ROOT/build/reports/jacoco/jacoco.xml"
BADGE_PATH="$PROJECT_ROOT/.github/badges/jacoco.svg"

export LC_ALL=C

cd "$PROJECT_ROOT"

case "$#" in
    0)
        printf '%s\n' "Running tests and generating the JaCoCo report..."
        ./kotlin task :kotbusta:jacoco-maven-plugin.report
        ;;
    1)
        if [ "$1" != "--from-report" ]; then
            printf 'Usage: %s [--from-report]\n' "${0##*/}" >&2
            exit 2
        fi
        ;;
    *)
        printf 'Usage: %s [--from-report]\n' "${0##*/}" >&2
        exit 2
        ;;
esac

if [ ! -f "$REPORT_PATH" ]; then
    printf 'JaCoCo report was not generated at %s\n' "$REPORT_PATH" >&2
    exit 1
fi

# JaCoCo writes the report aggregate counters after all nested counters.
# Reading the final instruction counter avoids double-counting packages,
# classes, and methods as the previous badge generator did.
COUNTERS=$(
    awk '
        {
            remaining = $0
            while (match(remaining, /<counter type="INSTRUCTION"[^>]*>/)) {
                aggregate = substr(remaining, RSTART, RLENGTH)
                remaining = substr(remaining, RSTART + RLENGTH)
            }
        }
        END {
            if (aggregate == "") {
                exit 1
            }

            missed = aggregate
            sub(/^.*missed="/, "", missed)
            sub(/".*$/, "", missed)

            covered = aggregate
            sub(/^.*covered="/, "", covered)
            sub(/".*$/, "", covered)

            if (missed !~ /^[0-9]+$/ || covered !~ /^[0-9]+$/) {
                exit 1
            }

            print missed, covered
        }
    ' "$REPORT_PATH"
) || {
    printf 'Unable to read the aggregate instruction counter from %s\n' "$REPORT_PATH" >&2
    exit 1
}

MISSED=${COUNTERS%% *}
COVERED=${COUNTERS#* }

if [ "$MISSED" = "$COUNTERS" ] || [ -z "$COVERED" ] || [ "${COVERED#* }" != "$COVERED" ]; then
    printf 'Unexpected JaCoCo instruction counter: %s\n' "$COUNTERS" >&2
    exit 1
fi

TOTAL=$((MISSED + COVERED))

if [ "$TOTAL" -eq 0 ]; then
    printf '%s\n' "JaCoCo reported no instructions" >&2
    exit 1
fi

COVERAGE=$(awk -v covered="$COVERED" -v total="$TOTAL" \
    'BEGIN { printf "%.1f", (covered / total) * 100 }')

COLOR=$(awk -v coverage="$COVERAGE" '
    BEGIN {
        if (coverage >= 80) {
            print "#4c1"
        } else if (coverage >= 60) {
            print "#dfb317"
        } else if (coverage >= 40) {
            print "#fe7d37"
        } else {
            print "#e05d44"
        }
    }
')

mkdir -p "$(dirname "$BADGE_PATH")"

cat > "$BADGE_PATH" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" width="110" height="20" role="img" aria-label="coverage: ${COVERAGE}%">
  <title>coverage: ${COVERAGE}%</title>
  <linearGradient id="s" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <clipPath id="r">
    <rect width="110" height="20" rx="3" fill="#fff"/>
  </clipPath>
  <g clip-path="url(#r)">
    <rect width="61" height="20" fill="#555"/>
    <rect x="61" width="49" height="20" fill="${COLOR}"/>
    <rect width="110" height="20" fill="url(#s)"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="110">
    <text aria-hidden="true" x="315" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="510">coverage</text>
    <text x="315" y="140" transform="scale(.1)" textLength="510">coverage</text>
    <text aria-hidden="true" x="855" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="390">${COVERAGE}%</text>
    <text x="855" y="140" transform="scale(.1)" textLength="390">${COVERAGE}%</text>
  </g>
</svg>
EOF

printf 'Coverage: %s%% (%s/%s instructions)\n' "$COVERAGE" "$COVERED" "$TOTAL"
printf 'Badge written to %s\n' "$BADGE_PATH"
