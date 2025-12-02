#!/usr/bin/env python3
"""
Generate weekly_cleaned.json by emulating WeeklyCleaner.generateCleanedWeekly()
Writes output to app/src/main/assets/weekly_cleaned.json
"""
import json
from pathlib import Path
from datetime import datetime, timedelta
import calendar

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
PERIODS = ASSETS / 'periods.json'
RAW_CACHE = ROOT / 'app' / 'build' / 'outputs' / 'weekly_raw.json'  # unlikely
EXAMPLE_RAW = ASSETS / 'example_weekly.json'
OUT = ASSETS / 'weekly_cleaned.json'


def load_periods(path: Path):
    jc_to_start_full = {}
    jc_to_display = {}
    if not path.exists():
        return jc_to_start_full, jc_to_display
    try:
        pj = json.loads(path.read_text(encoding='utf-8'))
        pdata = pj.get('data') or []
        for item in pdata:
            jc = str(item.get('jc', '')).strip()
            start = str(item.get('starttime', '')).strip()
            end = str(item.get('endtime', '')).strip()
            if jc and start:
                jc_to_start_full[jc] = start
                ds = ':'.join(start.split(':')[:2])
                de = ':'.join(end.split(':')[:2]) if end else ''
                jc_to_display[jc] = f"{ds}-{de}" if de else ds
    except Exception as e:
        print('failed parse periods.json', e)
    return jc_to_start_full, jc_to_display


def load_raw(path_asset: Path):
    # prefer a project-level cached raw if exists; otherwise example
    if path_asset.exists():
        try:
            return json.loads(path_asset.read_text(encoding='utf-8'))
        except Exception as e:
            print('failed to parse', path_asset, e)
    return None


def compute_week_start():
    # Monday of current week
    today = datetime.now()
    # isoweekday: Monday=1
    monday = today - timedelta(days=today.isoweekday() - 1)
    return monday.date()


def main():
    jc_full, jc_display = load_periods(PERIODS)
    raw = load_raw(EXAMPLE_RAW)
    if raw is None:
        print('no raw weekly data found')
        return 1
    data = raw.get('data') or []
    cleaned = {}

    week_start = compute_week_start()

    for item in data:
        try:
            wk = str(item.get('accountWeeknum', '')).strip()
            wk_int = int(wk) if wk != '' else None
        except Exception:
            wk_int = None
        if wk_int is None:
            continue
        wk_norm = 7 if wk_int == 0 else wk_int
        if not (1 <= wk_norm <= 7):
            continue
        date_obj = week_start + timedelta(days=(wk_norm - 1))
        date_str = date_obj.strftime('%Y-%m-%d')

        accountJtNo = str(item.get('accountJtNo', '')).strip()
        start_full = jc_full.get(accountJtNo, '')
        display_time = jc_display.get(accountJtNo, accountJtNo)
        if not start_full and '-' in accountJtNo:
            left = accountJtNo.split('-')[0].strip()
            if left in jc_full:
                start_full = jc_full[left]
                display_time = jc_display.get(left, display_time)

        time_key_part = start_full if start_full else accountJtNo
        key = f"{date_str} {time_key_part}"

        buildName = str(item.get('buildName', '')).strip()
        room = str(item.get('roomRoomnum', '')).strip()
        if buildName and room:
            location = f"{buildName}-{room}"
        elif buildName:
            location = buildName
        elif room:
            location = room
        else:
            location = ''

        subject = str(item.get('subjectSName', '')).strip()
        weekday_map = {1: 'Monday',2:'Tuesday',3:'Wednesday',4:'Thursday',5:'Friday',6:'Saturday',7:'Sunday'}
        weekday_label = weekday_map.get(wk_norm, str(wk_norm))

        out = {
            'weekday': weekday_label,
            'location': location,
            'subjectSName': subject,
            'time_display': display_time
        }

        cleaned.setdefault(key, []).append(out)

    # pretty write
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(cleaned, ensure_ascii=False, indent=2), encoding='utf-8')
    print('wrote', OUT)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
