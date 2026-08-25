#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    printf 'Usage: make release-seed [ADB=adb] [DEVICE=serial] [ANCHOR_DATE=YYYY-MM-DD]\n' >&2
    exit 2
fi

adb_bin=$1
device=$2
package_name=$3
anchor_date=$4

if ! command -v "${adb_bin}" >/dev/null 2>&1; then
    printf 'ERROR: adb executable not found: %s\n' "${adb_bin}" >&2
    exit 1
fi

if [[ ! "${anchor_date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    printf 'ERROR: ANCHOR_DATE must use YYYY-MM-DD: %s\n' "${anchor_date}" >&2
    exit 2
fi

parsed_anchor=$(date -u -d "${anchor_date}" +%F 2>/dev/null || true)
if [[ "${parsed_anchor}" != "${anchor_date}" ]]; then
    printf 'ERROR: invalid ANCHOR_DATE: %s\n' "${anchor_date}" >&2
    exit 2
fi

adb_command=("${adb_bin}")
if [[ -n "${device}" ]]; then
    adb_command+=(-s "${device}")
fi

if ! "${adb_command[@]}" get-state >/dev/null 2>&1; then
    printf 'ERROR: no ready adb device found.\n' >&2
    exit 1
fi

package_path=$("${adb_command[@]}" shell pm path "${package_name}" 2>/dev/null || true)
if [[ -z "${package_path//$'\r'/}" ]]; then
    printf 'ERROR: package is not installed: %s\n' "${package_name}" >&2
    exit 1
fi

date_from_anchor() {
    date -u -d "${anchor_date} ${1} days" +%F
}

cycle1_start=$(date_from_anchor -58)
cycle1_end=$(date_from_anchor -31)
cycle1_day2=$(date_from_anchor -57)
cycle1_day3=$(date_from_anchor -56)
cycle1_day4=$(date_from_anchor -55)

cycle2_start=$(date_from_anchor -30)
cycle2_end=$(date_from_anchor -6)
cycle2_day2=$(date_from_anchor -29)
cycle2_day3=$(date_from_anchor -28)
cycle2_day4=$(date_from_anchor -27)
cycle2_day5=$(date_from_anchor -26)

cycle3_start=$(date_from_anchor -5)
cycle3_day2=$(date_from_anchor -4)
cycle3_day3=$(date_from_anchor -3)
cycle3_day4=$(date_from_anchor -2)
anchor_day=${anchor_date}

c2_bbt0=$(date_from_anchor -23)
c2_bbt1=$(date_from_anchor -22)
c2_bbt2=$(date_from_anchor -21)
c2_bbt3=$(date_from_anchor -20)
c2_bbt4=$(date_from_anchor -19)
c2_bbt5=$(date_from_anchor -18)
c2_bbt6=$(date_from_anchor -17)
c2_bbt7=$(date_from_anchor -16)
c2_bbt8=$(date_from_anchor -15)
c3_bbt0=${cycle3_start}
c3_bbt1=${cycle3_day2}
c3_bbt2=${cycle3_day3}
c3_bbt3=${cycle3_day4}
c3_bbt4=$(date_from_anchor  -1)
c3_bbt5=${anchor_date}

timestamp="${anchor_date}T12:00:00Z"
output_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/demo-data"
mkdir -p "${output_dir}"
backup_file="${output_dir}/luteal-demo-backup.json"

cat >"${backup_file}" <<EOF
{
  "schema_version": 1,
  "exported_at": "${timestamp}",
  "app_version": "1.4.0-demo",
  "cycles": [
    {
      "id": "demo-cycle-1",
      "start_date": "${cycle1_start}",
      "end_date": "${cycle1_end}",
      "average_length_days": 28,
      "luteal_phase_length_days": 14,
      "period_days": [
        {"date": "${cycle1_start}", "bleeding_intensity": "HEAVY", "notes": "First recorded day", "symptom_ids": ["cramps"]},
        {"date": "${cycle1_day2}", "bleeding_intensity": "MEDIUM", "notes": "", "symptom_ids": []},
        {"date": "${cycle1_day3}", "bleeding_intensity": "LIGHT", "notes": "", "symptom_ids": []},
        {"date": "${cycle1_day4}", "bleeding_intensity": "SPOTTING", "notes": "", "symptom_ids": []}
      ]
    },
    {
      "id": "demo-cycle-2",
      "start_date": "${cycle2_start}",
      "end_date": "${cycle2_end}",
      "average_length_days": 28,
      "luteal_phase_length_days": 14,
      "period_days": [
        {"date": "${cycle2_start}", "bleeding_intensity": "HEAVY", "notes": "Cycle start", "symptom_ids": ["cramps"]},
        {"date": "${cycle2_day2}", "bleeding_intensity": "HEAVY", "notes": "", "symptom_ids": []},
        {"date": "${cycle2_day3}", "bleeding_intensity": "MEDIUM", "notes": "", "symptom_ids": []},
        {"date": "${cycle2_day4}", "bleeding_intensity": "LIGHT", "notes": "", "symptom_ids": []},
        {"date": "${cycle2_day5}", "bleeding_intensity": "SPOTTING", "notes": "", "symptom_ids": []}
      ]
    },
    {
      "id": "demo-cycle-3",
      "start_date": "${cycle3_start}",
      "end_date": null,
      "average_length_days": 28,
      "luteal_phase_length_days": 14,
      "period_days": [
        {"date": "${cycle3_start}", "bleeding_intensity": "HEAVY", "notes": "Current cycle", "symptom_ids": ["cramps"]},
        {"date": "${cycle3_day2}", "bleeding_intensity": "MEDIUM", "notes": "", "symptom_ids": []},
        {"date": "${cycle3_day3}", "bleeding_intensity": "LIGHT", "notes": "", "symptom_ids": []},
        {"date": "${cycle3_day4}", "bleeding_intensity": "SPOTTING", "notes": "", "symptom_ids": []}
      ]
    }
  ],
  "daily_entries": [
    {"date": "${cycle1_start}", "bleeding_intensity": "HEAVY", "pain_level": 3, "mood_level": 2, "energy_level": 2, "symptom_ids": ["cramps", "fatigue"], "notes": "Moderate cramps and fatigue", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt5}", "bleeding_intensity": null, "pain_level": 1, "mood_level": 4, "energy_level": 5, "symptom_ids": ["ovulation_pain"], "notes": "Good energy", "updated_at": "${timestamp}"},
    {"date": "${cycle2_start}", "bleeding_intensity": "HEAVY", "pain_level": 4, "mood_level": 1, "energy_level": 1, "symptom_ids": ["cramps", "headache"], "notes": "Headache and intense cramps", "updated_at": "${timestamp}"},
    {"date": "${cycle2_day2}", "bleeding_intensity": null, "pain_level": 1, "mood_level": 5, "energy_level": 4, "symptom_ids": ["high_libido"], "notes": "High energy day", "updated_at": "${timestamp}"},
    {"date": "${cycle3_start}", "bleeding_intensity": "HEAVY", "pain_level": 3, "mood_level": 2, "energy_level": 2, "symptom_ids": ["cramps"], "notes": "Current period", "updated_at": "${timestamp}"},
    {"date": "${anchor_day}", "bleeding_intensity": "SPOTTING", "pain_level": 1, "mood_level": 4, "energy_level": 3, "symptom_ids": ["headache"], "notes": "Light headache", "updated_at": "${timestamp}"}
  ],
  "symptom_logs": [
    {"id": "demo-symptom-1", "timestamp": "${timestamp}", "date": "${cycle1_start}", "symptom_id": "cramps", "severity": 3, "notes": "Lower abdominal cramps"},
    {"id": "demo-symptom-2", "timestamp": "${timestamp}", "date": "${cycle2_start}", "symptom_id": "headache", "severity": 4, "notes": "Headache"},
    {"id": "demo-symptom-3", "timestamp": "${timestamp}", "date": "${anchor_day}", "symptom_id": "headache", "severity": 1, "notes": "Mild headache"}
  ],
  "biomarker_observations": [
    {"date": "${c2_bbt0}", "bbt_celsius": 36.35, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt1}", "bbt_celsius": 36.30, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt2}", "bbt_celsius": 36.35, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt3}", "bbt_celsius": 36.40, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt4}", "bbt_celsius": 36.35, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "WET", "cervical_texture": "EGG_WHITE", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt5}", "bbt_celsius": 36.30, "bbt_time": "07:00", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "WET", "cervical_texture": "EGG_WHITE", "lh_test_result": "PEAK_POSITIVE", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt6}", "bbt_celsius": 36.65, "bbt_time": "07:15", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "NEGATIVE", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt7}", "bbt_celsius": 36.70, "bbt_time": "07:15", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "NEGATIVE", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c2_bbt8}", "bbt_celsius": 36.75, "bbt_time": "07:15", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "NEGATIVE", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt0}", "bbt_celsius": 36.40, "bbt_time": "07:10", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt1}", "bbt_celsius": 36.35, "bbt_time": "07:10", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt2}", "bbt_celsius": 36.30, "bbt_time": "07:10", "bbt_quality": "disturbed", "bbt_disturbances": ["POOR_SLEEP"], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt3}", "bbt_celsius": 36.35, "bbt_time": "07:10", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DRY", "cervical_texture": "STICKY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt4}", "bbt_celsius": 36.40, "bbt_time": "07:10", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"},
    {"date": "${c3_bbt5}", "bbt_celsius": 36.35, "bbt_time": "07:10", "bbt_quality": "normal", "bbt_disturbances": [], "cervical_sensation": "DAMP", "cervical_texture": "CREAMY", "lh_test_result": "LOW", "hcg_test_result": null, "notes": "", "updated_at": "${timestamp}"}
  ],
  "preferences": {
    "user_role": "PRIMARY_TRACKER",
    "locale": "fr",
    "track_pmdd": true,
    "track_pms": true,
    "track_endometriosis": false,
    "track_pcos": false,
    "track_perimenopause": false,
    "track_thyroid": false,
    "age_band": "age_30_34",
    "temperature_unit": "CELSIUS"
  }
}
EOF

printf '==> Generated demo backup: %s\n' "${backup_file}"
demo_json=$(tr -d '\n' < "${backup_file}")
demo_json_base64=$(printf '%s' "${demo_json}" | base64 | tr -d '\n')

printf '==> Opening Luteal backup import preview...\n'
"${adb_command[@]}" shell am force-stop "${package_name}"
"${adb_command[@]}" shell am start -W \
    -n "${package_name}/.AdbBackupImportActivity" \
    -t application/json \
    --es "${package_name}.extra.IMPORT_JSON_BASE64" "${demo_json_base64}"

printf '%s\n' 'The import preview is open in Luteal. Confirm Replace all to load the demo data.'
