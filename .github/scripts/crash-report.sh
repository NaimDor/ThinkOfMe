#!/usr/bin/env bash
# Prints Crashlytics crashes from the BigQuery export: one table per app and platform,
# plus the near-real-time *_REALTIME tables when streaming export is on.
set -uo pipefail
days="${DAYS:-7}"
case "$days" in (*[!0-9]*|'') echo "::error::days must be a number"; exit 1;; esac
dataset="$PROJECT:firebase_crashlytics"
tables=$(bq ls --format=json "$dataset" 2>/dev/null | python3 -c 'import json,sys; print("\n".join(t["tableReference"]["tableId"] for t in json.load(sys.stdin)))' || true)
if [ -z "$tables" ]; then
  echo "::error::No Crashlytics export yet. Firebase console → Project settings → Integrations → BigQuery → Link, turn on Crashlytics (with streaming). Data appears from then on."
  exit 1
fi
out="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
echo "## Crashes in the last $days days" >> "$out"
for table in $tables; do
  echo "### $table" | tee -a "$out"
  bq query --use_legacy_sql=false --format=pretty --max_rows=30 "
    SELECT issue_title AS issue, IFNULL(issue_subtitle, '') AS detail,
           CONCAT(application.display_version, ' (', application.build_version, ')') AS version,
           COUNT(*) AS crashes, COUNT(DISTINCT installation_uuid) AS users,
           FORMAT_TIMESTAMP('%Y-%m-%d %H:%M', MAX(event_timestamp)) AS last_utc
    FROM \`${PROJECT}.firebase_crashlytics.${table}\`
    WHERE event_timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL ${days} DAY) AND is_fatal
    GROUP BY issue, detail, version ORDER BY last_utc DESC" | tee -a "$out"
  echo "#### Latest stack per issue" | tee -a "$out"
  bq query --use_legacy_sql=false --format=prettyjson --max_rows=10 "
    SELECT issue_title AS issue, application.build_version AS build,
           FORMAT_TIMESTAMP('%Y-%m-%d %H:%M', event_timestamp) AS utc,
           device.model AS device, operating_system.display_version AS os,
           ARRAY(SELECT AS STRUCT e.type, e.exception_message, e.blamed,
                 ARRAY(SELECT CONCAT(IFNULL(f.symbol, ''), ' ', IFNULL(f.file, ''), ':', CAST(IFNULL(f.line, 0) AS STRING))
                       FROM UNNEST(e.frames) f WITH OFFSET o WHERE o < 8 ORDER BY o) AS top_frames
                 FROM UNNEST(exceptions) e) AS exceptions
    FROM \`${PROJECT}.firebase_crashlytics.${table}\`
    WHERE event_timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL ${days} DAY) AND is_fatal
    QUALIFY ROW_NUMBER() OVER (PARTITION BY issue_id ORDER BY event_timestamp DESC) = 1
    ORDER BY event_timestamp DESC" | tee -a "$out"
done
