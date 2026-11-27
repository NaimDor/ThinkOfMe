"""Summarises Crashlytics crashes from the BigQuery export (dataset firebase_crashlytics):
per app/platform table, each fatal issue by version (crashes, users, last seen), then the
latest stack of each issue. Writes to the GitHub step summary and stdout.

Environment: SERVICE_ACCOUNT (key JSON with BigQuery Data Viewer + Job User), PROJECT, DAYS.
"""
import json
import os
import sys

from google.api_core import exceptions
from google.cloud import bigquery
from google.oauth2 import service_account

days = int(os.environ.get("DAYS") or "7")
info = json.loads(os.environ["SERVICE_ACCOUNT"])
project = os.environ.get("PROJECT") or info["project_id"]
client = bigquery.Client(project=project, credentials=service_account.Credentials.from_service_account_info(info))
summary_path = os.environ.get("GITHUB_STEP_SUMMARY")


def out(text: str = "") -> None:
    print(text, flush=True)
    if summary_path:
        with open(summary_path, "a") as f:
            f.write(text + "\n")


try:
    tables = [t.table_id for t in client.list_tables(f"{project}.firebase_crashlytics")]
except exceptions.NotFound:
    out("No crashes exported yet: the firebase_crashlytics dataset is created with the first crash after linking.")
    sys.exit(0)
except exceptions.Forbidden as error:
    print(f"::error::The deploy service account can't read BigQuery: give it BigQuery Data Viewer and BigQuery Job User. ({error.message})")
    sys.exit(1)

if not tables:
    out("No crashes exported yet: the tables appear with the first exported crash.")
    sys.exit(0)

out(f"## Crashes in the last {days} days")
for table in tables:
    ref = f"`{project}.firebase_crashlytics.{table}`"
    out(f"\n### {table}\n")
    rows = list(client.query(f"""
        SELECT issue_title AS issue, IFNULL(issue_subtitle, '') AS detail,
               CONCAT(application.display_version, ' (', application.build_version, ')') AS version,
               COUNT(*) AS crashes, COUNT(DISTINCT installation_uuid) AS users,
               FORMAT_TIMESTAMP('%Y-%m-%d %H:%M', MAX(event_timestamp)) AS last_utc
        FROM {ref}
        WHERE event_timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL {days} DAY) AND is_fatal
        GROUP BY issue, detail, version ORDER BY last_utc DESC LIMIT 30""").result())
    if not rows:
        out("No fatal crashes.")
        continue
    out("| Issue | Detail | Version | Crashes | Users | Last (UTC) |\n|---|---|---|---|---|---|")
    for r in rows:
        out(f"| {r.issue} | {r.detail} | {r.version} | {r.crashes} | {r.users} | {r.last_utc} |")
    out("\n#### Latest stack per issue\n")
    for r in client.query(f"""
        SELECT issue_title AS issue, application.build_version AS build,
               FORMAT_TIMESTAMP('%Y-%m-%d %H:%M', event_timestamp) AS utc,
               device.model AS device, operating_system.display_version AS os,
               ARRAY(SELECT AS STRUCT e.type, e.exception_message AS message,
                     ARRAY(SELECT CONCAT(IFNULL(f.symbol, ''), ' (', IFNULL(f.file, ''), ':', CAST(IFNULL(f.line, 0) AS STRING), ')')
                           FROM UNNEST(e.frames) f WITH OFFSET o WHERE o < 8 ORDER BY o) AS frames
                     FROM UNNEST(exceptions) e) AS exceptions
        FROM {ref}
        WHERE event_timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL {days} DAY) AND is_fatal
        QUALIFY ROW_NUMBER() OVER (PARTITION BY issue_id ORDER BY event_timestamp DESC) = 1
        ORDER BY event_timestamp DESC LIMIT 10""").result():
        out(f"**{r.issue}**, build {r.build}, {r.utc} UTC, {r.device}, OS {r.os}\n```")
        for e in r.exceptions or []:
            out(f"{e['type']}: {e['message']}")
            for frame in e["frames"] or []:
                out(f"    at {frame}")
        out("```")
